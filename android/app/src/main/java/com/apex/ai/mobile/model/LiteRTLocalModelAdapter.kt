package com.apex.ai.mobile.model

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileNotFoundException

/**
 * Production adapter for Google FunctionGemma / MobileActions-270M running locally
 * on-device via the official Google AI Edge LiteRT-LM Android Runtime (com.google.ai.edge.litertlm).
 */
class LiteRTLocalModelAdapter(
    private val context: Context
) : LocalModelAdapter {

    override val isMock: Boolean = false

    private val _status = MutableStateFlow(ModelRuntimeStatus.UNINITIALIZED)
    override val status: StateFlow<ModelRuntimeStatus> = _status.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    override val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private val _lastError = MutableStateFlow<ModelErrorInfo?>(null)
    override val lastError: StateFlow<ModelErrorInfo?> = _lastError.asStateFlow()

    private var currentConfig: ModelConfig? = null
    private var engine: Engine? = null
    private var activeSession: Session? = null
    private var activeModelFile: File? = null

    private val initMutex = Mutex()

    @Volatile
    private var isCancelled = false

    /**
     * Resolves the on-device model file location from candidate app directories.
     */
    fun resolveModelFile(config: ModelConfig): File? {
        val candidates = mutableListOf<File>()

        // 1. Internal app files directory (primary: files/models/mobile_actions_q8_ekv1024.litertlm)
        candidates.add(File(context.filesDir, config.modelPath))
        candidates.add(File(context.filesDir, "models/${File(config.modelPath).name}"))
        candidates.add(File(context.filesDir, File(config.modelPath).name))

        // 2. External files directory (e.g. /Android/data/com.apex.ai.mobile.debug/files/models/...)
        context.getExternalFilesDir(null)?.let { extDir ->
            candidates.add(File(extDir, config.modelPath))
            candidates.add(File(extDir, "models/${File(config.modelPath).name}"))
            candidates.add(File(extDir, File(config.modelPath).name))
        }

        // Direct match
        for (file in candidates) {
            if (file.exists() && file.isFile && file.length() > 0) {
                return file
            }
        }

        // 3. Scan models directories for any compatible .litertlm file
        val scanDirs = listOfNotNull(
            File(context.filesDir, "models"),
            context.filesDir,
            context.getExternalFilesDir("models"),
            context.getExternalFilesDir(null)
        )
        for (dir in scanDirs) {
            if (dir.exists() && dir.isDirectory) {
                val matches = dir.listFiles { f ->
                    f.isFile && f.name.endsWith(".litertlm") && f.length() > 1024 * 1024
                }
                if (!matches.isNullOrEmpty()) {
                    return matches[0]
                }
            }
        }

        return null
    }

    /**
     * Expected path for diagnostic reporting and user provisioning.
     */
    fun getPrimaryExpectedPath(config: ModelConfig): String {
        return File(context.filesDir, config.modelPath).absolutePath
    }

    override suspend fun initialize(config: ModelConfig): Result<Unit> = withContext(Dispatchers.IO) {
        initMutex.withLock {
            _status.value = ModelRuntimeStatus.LOADING
            _loadingProgress.value = 0.1f
            currentConfig = config
            _lastError.value = null

            val expectedPath = getPrimaryExpectedPath(config)
            val modelFile = resolveModelFile(config)

            if (modelFile == null || !modelFile.exists()) {
                val error = ModelErrorInfo(
                    code = "MODEL_NOT_FOUND",
                    message = "MobileActions-270M weights file not found on device.",
                    expectedPath = expectedPath,
                    resolutionGuide = "Transfer the model file (mobile_actions_q8_ekv1024.litertlm) into: $expectedPath, then tap 'CHECK / RELOAD LOCAL MODEL' in Settings."
                )
                _status.value = ModelRuntimeStatus.MODEL_MISSING
                _loadingProgress.value = 0f
                _lastError.value = error
                Log.w(TAG, "LiteRT-LM initialization failed: model not found at $expectedPath")
                return@withContext Result.failure(FileNotFoundException(error.message))
            }

            // Verify FlatBuffer header to ensure compatibility with LiteRT-LM ('RTLM')
            try {
                ModelFormatValidator.validateForLiteRtLmEngine(modelFile)
            } catch (formatEx: IllegalArgumentException) {
                val error = ModelErrorInfo(
                    code = "INCOMPATIBLE_MODEL_FORMAT",
                    message = formatEx.message ?: "Incompatible model format",
                    expectedPath = modelFile.absolutePath,
                    resolutionGuide = "Ensure the model has the 'RTLM' identifier (LiteRT-LM format) and was not compiled as standard 'TFL3' format."
                )
                _status.value = ModelRuntimeStatus.ERROR
                _loadingProgress.value = 0f
                _lastError.value = error
                Log.e(TAG, "Model validation failed: ${formatEx.message}", formatEx)
                return@withContext Result.failure(formatEx)
            }

            try {
                _loadingProgress.value = 0.3f
                Log.i(TAG, "Initializing LiteRT-LM Engine with model file: ${modelFile.absolutePath} (size: ${modelFile.length()} bytes)")

                // Clean up previous instances if reloading
                try {
                    activeSession?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing previous session: ${e.message}", e)
                }
                activeSession = null

                try {
                    engine?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing previous engine: ${e.message}", e)
                }
                engine = null

                _loadingProgress.value = 0.5f

                val cacheDir = context.cacheDir.absolutePath
                val engineConfig = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = if (config.useGpuDelegate) Backend.GPU() else Backend.CPU(),
                    cacheDir = cacheDir
                )

                _loadingProgress.value = 0.7f
                Log.d(TAG, "Instantiating LiteRT-LM Engine with backend: ${if (config.useGpuDelegate) "GPU" else "CPU"}")

                var newEngine: Engine? = null
                try {
                    newEngine = Engine(engineConfig)
                    newEngine.initialize()
                } catch (initErr: Exception) {
                    if (config.useGpuDelegate) {
                        Log.w(TAG, "LiteRT-LM GPU init failed, falling back to CPU: ${initErr.message}", initErr)
                        val cpuConfig = EngineConfig(
                            modelPath = modelFile.absolutePath,
                            backend = Backend.CPU(),
                            cacheDir = cacheDir
                        )
                        newEngine = Engine(cpuConfig)
                        newEngine.initialize()
                    } else {
                        throw initErr
                    }
                }

                engine = newEngine
                activeModelFile = modelFile

                _loadingProgress.value = 1.0f
                _status.value = ModelRuntimeStatus.READY
                _lastError.value = null
                Log.i(TAG, "LiteRT-LM Engine successfully initialized and ready for inference.")

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "LiteRT-LM engine failed to initialize with model file ${modelFile.absolutePath}: ${e.message}", e)
                val error = ModelErrorInfo(
                    code = "MODEL_INIT_FAILED",
                    message = "LiteRT-LM engine failed to initialize: ${e.message}\nStack trace:\n${e.stackTraceToString()}",
                    expectedPath = modelFile.absolutePath,
                    resolutionGuide = "Verify that the model file is a valid LiteRT-LM bundle (.litertlm) compiled with 'RTLM' header."
                )
                _status.value = ModelRuntimeStatus.ERROR
                _loadingProgress.value = 0f
                _lastError.value = error
                Result.failure(e)
            }
        }
    }

    override suspend fun generate(request: GenerationRequest): Result<GenerationResult> = withContext(Dispatchers.Default) {
        val currentEngine = engine
        if (_status.value != ModelRuntimeStatus.READY || currentEngine == null) {
            val statusCurrent = _status.value
            val errInfo = _lastError.value
            val msg = when (statusCurrent) {
                ModelRuntimeStatus.MODEL_MISSING ->
                    "Model file not found. Please transfer MobileActions-270M (.litertlm) to ${errInfo?.expectedPath ?: "app models directory"}."
                ModelRuntimeStatus.LOADING ->
                    "Model is still loading into memory. Please wait a moment."
                else ->
                    "LiteRT-LM model runtime is not ready (Status: $statusCurrent). Error: ${errInfo?.message ?: "None"}"
            }
            return@withContext Result.failure(IllegalStateException(msg))
        }

        _status.value = ModelRuntimeStatus.INFERRING
        isCancelled = false
        val startTime = System.currentTimeMillis()

        try {
            val config = currentConfig ?: ModelConfig()
            val prompt = FunctionGemmaPromptFormatter.formatPrompt(
                systemInstruction = request.systemInstruction ?: DEFAULT_SYSTEM_INSTRUCTION,
                toolsDeclarations = request.availableTools,
                history = request.conversationHistory,
                userPrompt = request.prompt
            )

            withTimeout(config.timeoutMillis) {
                if (isCancelled) {
                    throw CancellationException("LiteRT-LM generation cancelled")
                }

                // Execute genuine on-device inference via LiteRT-LM Session
                val rawOutput = withContext(Dispatchers.IO) {
                    if (isCancelled) throw CancellationException("LiteRT-LM generation cancelled")

                    val samplerConfig = SamplerConfig(
                        topK = config.topK,
                        topP = 0.95,
                        temperature = config.temperature.toDouble(),
                        seed = 0
                    )
                    val sessionConfig = SessionConfig(samplerConfig = samplerConfig)
                    val session = currentEngine.createSession(sessionConfig)
                    activeSession = session

                    try {
                        val input = listOf(InputData.Text(prompt))
                        session.generateContent(input)
                    } finally {
                        try {
                            session.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error closing session: ${e.message}", e)
                        }
                        if (activeSession === session) {
                            activeSession = null
                        }
                    }
                }

                val (cleanText, toolCalls) = FunctionGemmaPromptFormatter.parseOutput(rawOutput)
                val latency = System.currentTimeMillis() - startTime

                val finalText = if (cleanText.isBlank() && toolCalls.isEmpty()) {
                    "APEX (MobileActions-270M): No device action was identified for this request. Ask me to check battery, launch apps, toggle flashlight, open settings, or take a photo."
                } else {
                    cleanText
                }

                val result = GenerationResult(
                    text = finalText,
                    toolCalls = toolCalls,
                    latencyMs = latency,
                    tokenCount = rawOutput.length / 4,
                    finishReason = "stop"
                )

                _status.value = ModelRuntimeStatus.READY
                Result.success(result)
            }
        } catch (e: CancellationException) {
            _status.value = ModelRuntimeStatus.CANCELLED
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "LiteRT-LM inference execution error: ${e.message}", e)
            _status.value = ModelRuntimeStatus.ERROR
            _lastError.value = ModelErrorInfo(
                code = "INFERENCE_FAILED",
                message = "Inference execution error: ${e.message}\nStack trace:\n${e.stackTraceToString()}",
                resolutionGuide = "Try reducing context length or re-initializing the model."
            )
            Result.failure(e)
        } finally {
            if (_status.value == ModelRuntimeStatus.INFERRING) {
                _status.value = ModelRuntimeStatus.READY
            }
        }
    }

    override fun cancelCurrentGeneration() {
        isCancelled = true
        try {
            activeSession?.cancelProcess()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling LiteRT-LM session: ${e.message}", e)
        }
        _status.value = ModelRuntimeStatus.CANCELLED
    }

    override suspend fun unload(): Result<Unit> = withContext(Dispatchers.IO) {
        initMutex.withLock {
            try {
                activeSession?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing session during unload: ${e.message}", e)
            }
            activeSession = null

            try {
                engine?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing engine during unload: ${e.message}", e)
            }
            engine = null
            activeModelFile = null
            _status.value = ModelRuntimeStatus.UNINITIALIZED
            _loadingProgress.value = 0f
            _lastError.value = null
            Result.success(Unit)
        }
    }

    override fun checkHealth(): ModelHealth {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val modelFile = activeModelFile

        return when (_status.value) {
            ModelRuntimeStatus.READY -> {
                val sizeMb = if (modelFile != null && modelFile.exists()) modelFile.length() / (1024 * 1024) else 0L
                ModelHealth.Healthy(
                    ready = true,
                    memoryUsedMb = usedMem,
                    device = "Google LiteRT-LM (MobileActions-270M, ${sizeMb}MB weights loaded)",
                    isRealInference = true
                )
            }
            ModelRuntimeStatus.MODEL_MISSING -> {
                ModelHealth.Offline(
                    reason = "Model weights not found. File required in models directory.",
                    errorInfo = _lastError.value
                )
            }
            ModelRuntimeStatus.LOADING -> {
                ModelHealth.Degraded(
                    message = "Loading model weights into memory (${(_loadingProgress.value * 100).toInt()}%)...",
                    memoryUsedMb = usedMem
                )
            }
            ModelRuntimeStatus.ERROR -> {
                ModelHealth.Offline(
                    reason = _lastError.value?.message ?: "LiteRT-LM engine error occurred",
                    errorInfo = _lastError.value
                )
            }
            else -> {
                ModelHealth.Offline(
                    reason = "Status: ${_status.value}",
                    errorInfo = _lastError.value
                )
            }
        }
    }

    override fun getSupportedRuntimes(): List<String> = listOf(
        "LiteRT-LM-CPU",
        "LiteRT-LM-GPU"
    )

    fun getActiveModelFile(): File? = activeModelFile

    companion object {
        private const val TAG = "LiteRTLocalModelAdapter"

        const val DEFAULT_SYSTEM_INSTRUCTION =
            "APEX is an intelligent on-device personal executive assistant running on Android. " +
            "Be concise, helpful, and natural. Use the provided function declarations to perform phone actions."
    }
}
