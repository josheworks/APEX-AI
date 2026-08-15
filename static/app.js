// APEX v0.1 Voice Interface Application
// Conversation Mode
//
// Features:
// - Time-aware spoken welcome
// - Text chat
// - Voice recording
// - Automatic silence detection
// - Voice transcription through /voice
// - Text-to-speech
// - Tool activity indicators
// - Conversation Mode
// - Reliable "OK APEX" stop command
// - Emoji -> spoken meaning for TTS
// - Conversation Mode audio visual interface
// - Click microphone while speaking to interrupt APEX


document.addEventListener('DOMContentLoaded', () => {

    // =========================================================
    // DOM ELEMENTS
    // =========================================================

    const statusContainer =
        document.querySelector('.status-container');

    const statusText =
        document.getElementById('statusText');

    const micBtn =
        document.getElementById('micBtn');

    const conversationBtn =
        document.getElementById('conversationBtn');

    const chatForm =
        document.getElementById('chatForm');

    const userInput =
        document.getElementById('userInput');

    const chatArea =
        document.getElementById('chatArea');

    const welcomeCard =
        document.getElementById('welcomeCard');

    const notificationBanner =
        document.getElementById('notificationBanner');

    const notificationMessage =
        document.getElementById('notificationMessage');


    // =========================================================
    // STATE
    // =========================================================

    let currentState = 'READY';

    let isProcessing = false;

    let conversationMode = false;


    // =========================================================
    // CONVERSATION MODE AUDIO UI
    // =========================================================
    //
    // When Conversation Mode is active:
    //
    // Normal:
    //      CHAT LAYOUT
    //
    // Conversation Mode:
    //      🎙️
    //      APEX VOICE MODE
    //      LISTENING / THINKING / SPEAKING
    //
    // Chat is restored when Conversation Mode ends.
    //


    let conversationVisual = null;


    function createConversationVisual() {

        if (conversationVisual) {
            return;
        }


        conversationVisual =
            document.createElement('div');


        conversationVisual.id =
            'conversationVisual';


        conversationVisual.innerHTML = `
            <div class="conversation-orb">
                <div class="conversation-orb-inner">
                    🎙️
                </div>
            </div>

            <div class="conversation-title">
                APEX VOICE MODE
            </div>

            <div
                class="conversation-state"
                id="conversationVisualState"
            >
                READY
            </div>

            <div class="conversation-hint">
                Say "OK APEX" to stop
            </div>
        `;


        conversationVisual.style.cssText = `
            flex: 1;
            min-height: 0;
            display: none;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            text-align: center;
            padding: 30px;
            overflow: hidden;
        `;


        const style =
            document.createElement('style');


        style.id =
            'apex-conversation-visual-style';


        style.textContent = `

            #conversationVisual {
                position: relative;
            }

            #conversationVisual::before {
                content: "";
                position: absolute;
                width: 300px;
                height: 300px;
                border-radius: 50%;
                background: radial-gradient(
                    circle,
                    rgba(0, 242, 254, 0.10),
                    transparent 70%
                );
                pointer-events: none;
            }

            .conversation-orb {
                width: 170px;
                height: 170px;
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                border: 2px solid rgba(0, 242, 254, 0.7);
                box-shadow:
                    0 0 25px rgba(0, 242, 254, 0.25),
                    inset 0 0 30px rgba(0, 242, 254, 0.08);
                transition:
                    transform 0.3s ease,
                    box-shadow 0.3s ease,
                    border-color 0.3s ease;
                animation: apexVoiceIdle 3s ease-in-out infinite;
                z-index: 1;
            }

            .conversation-orb-inner {
                width: 130px;
                height: 130px;
                border-radius: 50%;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 4rem;
                background:
                    radial-gradient(
                        circle,
                        rgba(30, 41, 59, 0.95),
                        rgba(15, 23, 42, 0.95)
                    );
            }

            .conversation-title {
                margin-top: 28px;
                font-family: var(--font-mono);
                font-size: 0.85rem;
                letter-spacing: 2px;
                color: var(--text-secondary);
                z-index: 1;
            }

            .conversation-state {
                margin-top: 10px;
                font-family: var(--font-mono);
                font-size: 1rem;
                font-weight: 700;
                letter-spacing: 1.5px;
                color: var(--accent-cyan);
                z-index: 1;
            }

            .conversation-hint {
                margin-top: 18px;
                font-size: 0.78rem;
                color: var(--text-secondary);
                opacity: 0.8;
                z-index: 1;
            }

            #conversationVisual.visual-listening
            .conversation-orb {
                border-color: var(--status-listening);
                box-shadow:
                    0 0 35px rgba(255, 0, 85, 0.55),
                    inset 0 0 35px rgba(255, 0, 85, 0.10);
                animation: apexVoiceListening 1s ease-in-out infinite;
            }

            #conversationVisual.visual-thinking
            .conversation-orb {
                border-color: var(--status-thinking);
                box-shadow:
                    0 0 35px rgba(168, 85, 247, 0.55),
                    inset 0 0 35px rgba(168, 85, 247, 0.10);
                animation: apexVoiceThinking 0.8s linear infinite;
            }

            #conversationVisual.visual-speaking
            .conversation-orb {
                border-color: var(--status-speaking);
                box-shadow:
                    0 0 40px rgba(16, 185, 129, 0.55),
                    inset 0 0 40px rgba(16, 185, 129, 0.10);
                animation: apexVoiceSpeaking 0.55s ease-in-out infinite;
            }

            @keyframes apexVoiceIdle {
                0%, 100% {
                    transform: scale(1);
                }

                50% {
                    transform: scale(1.04);
                }
            }

            @keyframes apexVoiceListening {
                0%, 100% {
                    transform: scale(1);
                }

                50% {
                    transform: scale(1.12);
                }
            }

            @keyframes apexVoiceThinking {
                0% {
                    transform: rotate(0deg) scale(1);
                }

                50% {
                    transform: rotate(180deg) scale(1.08);
                }

                100% {
                    transform: rotate(360deg) scale(1);
                }
            }

            @keyframes apexVoiceSpeaking {
                0%, 100% {
                    transform: scale(1);
                }

                25% {
                    transform: scale(1.08);
                }

                50% {
                    transform: scale(1.16);
                }

                75% {
                    transform: scale(1.08);
                }
            }

        `;


        document.head.appendChild(style);

        chatArea.parentNode.insertBefore(
            conversationVisual,
            chatArea.nextSibling
        );
    }


    function updateConversationVisualState(
        state
    ) {

        if (!conversationVisual) {
            return;
        }


        const visualState =
            document.getElementById(
                'conversationVisualState'
            );


        conversationVisual.classList.remove(
            'visual-listening',
            'visual-thinking',
            'visual-speaking'
        );


        if (visualState) {
            visualState.textContent =
                state;
        }


        if (state === 'LISTENING') {

            conversationVisual.classList.add(
                'visual-listening'
            );

        } else if (state === 'THINKING') {

            conversationVisual.classList.add(
                'visual-thinking'
            );

        } else if (state === 'SPEAKING') {

            conversationVisual.classList.add(
                'visual-speaking'
            );
        }
    }


    function setConversationVisualMode(
        enabled
    ) {

        createConversationVisual();


        if (enabled) {

            // Hide normal chat layout.
            chatArea.style.display =
                'none';


            // Show voice interface.
            conversationVisual.style.display =
                'flex';


            updateConversationVisualState(
                currentState
            );

        } else {

            // Restore normal chat layout.
            conversationVisual.style.display =
                'none';


            chatArea.style.display =
                'flex';
        }
    }


    // =========================================================
    // OK APEX STOP COMMAND
    // =========================================================

    let recognition = null;


    const recognitionSupported = !!(
        window.SpeechRecognition ||
        window.webkitSpeechRecognition
    );


    let stopCommandDetected = false;


    // Rolling speech-recognition buffer.
    //
    // This handles:
    //
    // "Okay"
    // "APEX"
    //
    // arriving as separate recognition results.

    let recognitionBuffer = '';


    function normalizeRecognitionText(
        text
    ) {

        return (text || '')
            .toLowerCase()
            .replace(
                /[.,!?;:'"()[\]{}]/g,
                ' '
            )
            .replace(
                /\s+/g,
                ' '
            )
            .trim();
    }


    function checkForStopCommand(
        text
    ) {

        const normalized =
            normalizeRecognitionText(
                text
            );


        if (!normalized) {
            return false;
        }


        return /\b(?:ok|okay)\s+apex\b/i.test(
            normalized
        );
    }


    function startRecognition() {

        if (
            !recognitionSupported ||
            recognition
        ) {
            return;
        }


        try {

            const Rec =
                window.SpeechRecognition ||
                window.webkitSpeechRecognition;


            recognition =
                new Rec();


            recognition.continuous =
                true;


            recognition.interimResults =
                true;


            recognition.lang =
                'en-US';


            recognitionBuffer =
                '';


            recognition.onresult =
                (event) => {

                    try {

                        let latestTranscript =
                            '';


                        for (
                            let i =
                                event.resultIndex;
                            i <
                                event.results.length;
                            i++
                        ) {

                            const result =
                                event.results[i];


                            const transcript =
                                result[0]
                                    .transcript ||
                                '';


                            latestTranscript +=
                                ' ' +
                                transcript;
                        }


                        const normalized =
                            normalizeRecognitionText(
                                latestTranscript
                            );


                        if (!normalized) {
                            return;
                        }


                        recognitionBuffer =
                            `${recognitionBuffer} ${normalized}`
                                .replace(
                                    /\s+/g,
                                    ' '
                                )
                                .trim();


                        // Keep only recent words.
                        const words =
                            recognitionBuffer.split(
                                ' '
                            );


                        recognitionBuffer =
                            words
                                .slice(-8)
                                .join(' ');


                        // -------------------------------------------------
                        // OK APEX
                        // -------------------------------------------------

                        if (
                            conversationMode &&
                            !stopCommandDetected &&
                            checkForStopCommand(
                                recognitionBuffer
                            )
                        ) {

                            stopCommandDetected =
                                true;


                            console.log(
                                'APEX: OK APEX stop command detected.'
                            );


                            // Stop recognition immediately.
                            stopRecognition();


                            // Stop Conversation Mode silently (no UI banner).
                            doStopConversationMode({
                                suppressNotification: true
                            });


                            recognitionBuffer =
                                '';


                            return;
                        }

                    } catch (error) {

                        console.warn(
                            'Recognition processing error:',
                            error
                        );
                    }
                };


            recognition.onend =
                () => {

                    if (
                        conversationMode &&
                        currentState ===
                            'LISTENING' &&
                        !stopCommandDetected
                    ) {

                        try {

                            recognition.start();

                        } catch (error) {

                            // Ignore duplicate start errors.
                        }
                    }
                };


            recognition.onerror =
                (error) => {

                    console.warn(
                        'SpeechRecognition error:',
                        error
                    );
                };


            try {

                recognition.start();

            } catch (error) {

                console.warn(
                    'SpeechRecognition start error:',
                    error
                );
            }

        } catch (error) {

            console.warn(
                'SpeechRecognition not available:',
                error
            );


            recognition =
                null;
        }
    }


    function stopRecognition() {

        if (!recognition) {

            recognitionBuffer =
                '';

            return;
        }


        try {

            recognition.onresult =
                null;

            recognition.onend =
                null;

            recognition.onerror =
                null;


            recognition.stop();

        } catch (error) {

            // Ignore stop errors.
        }


        recognition =
            null;


        recognitionBuffer =
            '';
    }


    // =========================================================
    // TIME-AWARE WELCOME
    // =========================================================

    function setTimeAwareWelcome() {

        const welcomeTitle =
            document.getElementById(
                'welcomeTitle'
            );


        const welcomeMessage =
            document.getElementById(
                'welcomeMessage'
            );


        if (
            !welcomeTitle ||
            !welcomeMessage
        ) {
            return;
        }


        const hour =
            new Date().getHours();


        let greetings;


        if (
            hour >= 5 &&
            hour < 12
        ) {

            greetings = [
                "Good morning, Boss. What's on your mind?",
                "Morning, Boss. What can I do for you?",
                "Good morning, Boss. Ready to get things moving?"
            ];

        } else if (
            hour >= 12 &&
            hour < 17
        ) {

            greetings = [
                "Good afternoon, Boss. How's your day going?",
                "Good afternoon, Boss. What are we working on?",
                "Hey Boss, how can I help you?"
            ];

        } else if (
            hour >= 17 &&
            hour < 21
        ) {

            greetings = [
                "Good evening, Boss. How was college today?",
                "Hey Boss, you're back. How was your day?",
                "Good evening, Boss. What's on your mind?"
            ];

        } else {

            greetings = [
                "Still working, Boss? What's on your mind?",
                "You're still up, Boss? What can I do for you?",
                "Hey Boss, what's on your mind tonight?"
            ];
        }


        const greeting =
            greetings[
                Math.floor(
                    Math.random() *
                    greetings.length
                )
            ];


        welcomeTitle.textContent =
            greeting;


        welcomeMessage.textContent =
            "What can I do for you?";


        if (
            'speechSynthesis' in window
        ) {

            setTimeout(() => {

                speakResponse(
                    greeting
                );

            }, 500);
        }
    }


    // =========================================================
    // MEDIA RECORDER
    // =========================================================

    let mediaRecorder = null;

    let audioChunks = [];

    let mediaStream = null;


    // =========================================================
    // SILENCE DETECTION
    // =========================================================

    let audioContext = null;

    let analyser = null;

    let silenceCheckId = null;

    let silenceStart = null;

    let speechDetected = false;


    const SILENCE_THRESHOLD =
        0.015;


    const SILENCE_DURATION =
        1200;


    // =========================================================
    // MEDIA RECORDER SUPPORT
    // =========================================================

    const isMediaRecorderSupported = !!(
        navigator.mediaDevices &&
        navigator.mediaDevices.getUserMedia &&
        window.MediaRecorder
    );


    if (
        !isMediaRecorderSupported
    ) {

        showNotification(
            "Audio recording is not supported in this browser. " +
            "You can still use text input.",
            8000
        );
    }


    // =========================================================
    // TOOL LABELS
    // =========================================================

    const TOOL_LABELS = {

        "get_current_time":
            "Checking the time",

        "get_current_date":
            "Checking the date",

        "open_website":
            "Opening website",

        "web_search":
            "Searching the web",

        "calculate":
            "Calculating",

        "get_system_info":
            "Reading system info",

        "open_local_app":
            "Opening application"
    };


    // =========================================================
    // STATE MACHINE
    // =========================================================

    function setState(
        newState
    ) {

        currentState =
            newState;


        if (statusText) {

            statusText.textContent =
                newState;
        }


        if (statusContainer) {

            statusContainer.className =
                'status-container';


            statusContainer.classList.add(
                `status-${newState.toLowerCase()}`
            );
        }


        if (micBtn) {

            micBtn.classList.toggle(
                'listening',
                newState ===
                    'LISTENING'
            );


            if (
                newState ===
                'LISTENING'
            ) {

                micBtn.title =
                    'Listening...';

            } else if (
                newState ===
                'THINKING'
            ) {

                micBtn.title =
                    'Processing...';

            } else if (
                newState ===
                'SPEAKING'
            ) {

                micBtn.title =
                    'Click to interrupt APEX';

            } else {

                micBtn.title =
                    'Click to start listening';
            }
        }


        isProcessing = (
            newState === 'THINKING' ||
            newState === 'SPEAKING' ||
            newState === 'LISTENING'
        );


        // Update audio interface.
        if (
            conversationMode
        ) {

            updateConversationVisualState(
                newState
            );
        }


        // Update conversation button.

        if (conversationBtn) {

            if (
                conversationMode
            ) {

                conversationBtn.textContent =
                    '🛑 End Conversation';


                conversationBtn.classList.add(
                    'conversation-active'
                );


                conversationBtn.title =
                    'Stop conversation mode';

            } else {

                conversationBtn.textContent =
                    '💬 Conversation';


                conversationBtn.classList.remove(
                    'conversation-active'
                );


                conversationBtn.title =
                    'Start conversation mode';
            }
        }
    }


    // =========================================================
    // NOTIFICATION
    // =========================================================

    function showNotification(
        message,
        duration = 5000
    ) {

        if (
            !notificationMessage ||
            !notificationBanner
        ) {
            return;
        }


        notificationMessage.textContent =
            message;


        notificationBanner.classList.remove(
            'hidden'
        );


        setTimeout(() => {

            notificationBanner.classList.add(
                'hidden'
            );

        }, duration);
    }


    // =========================================================
    // CHAT MESSAGE
    // =========================================================

    function appendMessage(
        sender,
        text
    ) {

        if (welcomeCard) {

            welcomeCard.style.display =
                'none';
        }


        const msgBubble =
            document.createElement(
                'div'
            );


        msgBubble.classList.add(
            'message-bubble',
            sender === 'USER'
                ? 'user-message'
                : 'apex-message'
        );


        const msgHeader =
            document.createElement(
                'div'
            );


        msgHeader.classList.add(
            'message-header'
        );


        msgHeader.textContent =
            sender;


        const msgBody =
            document.createElement(
                'div'
            );


        msgBody.classList.add(
            'message-body'
        );


        msgBody.textContent =
            text;


        msgBubble.appendChild(
            msgHeader
        );


        msgBubble.appendChild(
            msgBody
        );


        chatArea.appendChild(
            msgBubble
        );


        chatArea.scrollTop =
            chatArea.scrollHeight;
    }


    // =========================================================
    // TOOL INDICATOR
    // =========================================================

    function appendToolIndicator(
        toolName
    ) {

        if (welcomeCard) {

            welcomeCard.style.display =
                'none';
        }


        const label =
            TOOL_LABELS[toolName] ||
            toolName;


        const indicator =
            document.createElement(
                'div'
            );


        indicator.classList.add(
            'tool-indicator'
        );


        indicator.innerHTML =
            `<span class="tool-icon">⚡</span>` +
            `<span class="tool-label">` +
            `APEX › ${label}...` +
            `</span>`;


        indicator.id =
            'tool-indicator-active';


        chatArea.appendChild(
            indicator
        );


        chatArea.scrollTop =
            chatArea.scrollHeight;
    }


    function removeToolIndicator() {

        const element =
            document.getElementById(
                'tool-indicator-active'
            );


        if (element) {

            element.remove();
        }
    }


    // =========================================================
    // SEND TEXT MESSAGE
    // =========================================================

    async function sendTextMessage(
        text
    ) {

        if (
            !text ||
            text.trim() === ''
        ) {
            return;
        }


        appendMessage(
            'USER',
            text
        );


        userInput.value =
            '';


        setState(
            'THINKING'
        );


        try {

            const response =
                await fetch(
                    '/chat',
                    {
                        method: 'POST',

                        headers: {
                            'Content-Type':
                                'application/json'
                        },

                        body: JSON.stringify({
                            message: text
                        })
                    }
                );


            const data =
                await response.json();


            if (
                data.tool_used
            ) {

                appendToolIndicator(
                    data.tool_used
                );


                await new Promise(
                    resolve =>
                        setTimeout(
                            resolve,
                            400
                        )
                );


                removeToolIndicator();
            }


            if (
                data.success
            ) {

                appendMessage(
                    'APEX',
                    data.response
                );


                speakResponse(
                    data.response
                );

            } else if (
                data.error_type ===
                'QUOTA_EXHAUSTED'
            ) {

                const msg =
                    "Oops, my AI brain has hit " +
                    "today's free quota 😅. " +
                    "My built-in tools are still " +
                    "available! Try: 'What time is it?', " +
                    "'Open YouTube', or " +
                    "'Calculate 25 * 17'.";


                appendMessage(
                    'APEX',
                    msg
                );


                showNotification(
                    "AI quota exhausted. " +
                    "Built-in tools still work!",
                    7000
                );


                handleResponseFinished();

            } else {

                const errorText =
                    data.error ||
                    'Failed to get response from APEX.';


                appendMessage(
                    'APEX',
                    `[Error]: ${errorText}`
                );


                showNotification(
                    errorText
                );


                handleResponseFinished();
            }

        } catch (err) {

            console.error(
                'Fetch error:',
                err
            );


            appendMessage(
                'APEX',
                '[Error]: Network error. ' +
                'Could not connect to APEX backend server.'
            );


            showNotification(
                'Network error. Could not connect to APEX backend.'
            );


            handleResponseFinished();
        }
    }


    // =========================================================
    // SEND VOICE MESSAGE
    // =========================================================

    async function sendVoiceMessage(
        audioBlob,
        mimeType
    ) {

        setState(
            'THINKING'
        );


        const formData =
            new FormData();


        const extension =
            mimeType.includes('mp4')
                ? 'mp4'
                : 'webm';


        formData.append(
            'file',
            audioBlob,
            `recording.${extension}`
        );


        try {

            const response =
                await fetch(
                    '/voice',
                    {
                        method: 'POST',
                        body: formData
                    }
                );


            const data =
                await response.json();


            if (
                data.tool_used
            ) {

                appendToolIndicator(
                    data.tool_used
                );


                await new Promise(
                    resolve =>
                        setTimeout(
                            resolve,
                            400
                        )
                );


                removeToolIndicator();
            }


            if (
                data.success
            ) {

                appendMessage(
                    'USER',
                    data.transcription
                );


                appendMessage(
                    'APEX',
                    data.response
                );


                speakResponse(
                    data.response
                );

            } else if (
                data.error_type ===
                'QUOTA_EXHAUSTED'
            ) {

                const msg =
                    "Oops, my AI brain has hit " +
                    "today's free quota 😅. " +
                    "My built-in tools are still " +
                    "available!";


                appendMessage(
                    'APEX',
                    msg
                );


                showNotification(
                    "AI quota exhausted.",
                    7000
                );


                handleResponseFinished();

            } else {

                const errorText =
                    data.error ||
                    'Failed to process voice input.';


                showNotification(
                    errorText
                );


                handleResponseFinished();
            }

        } catch (err) {

            console.error(
                'Voice upload error:',
                err
            );


            showNotification(
                'Network error while sending audio to APEX.'
            );


            handleResponseFinished();
        }
    }


    // =========================================================
    // RESPONSE FINISHED
    // =========================================================

    function handleResponseFinished() {

        setState(
            'READY'
        );


        if (
            conversationMode
        ) {

            setTimeout(() => {

                if (
                    conversationMode &&
                    currentState ===
                        'READY'
                ) {

                    startRecording();
                }

            }, 500);
        }
    }


    // =========================================================
    // START SILENCE DETECTION
    // =========================================================

    function startSilenceDetection() {

        if (!mediaStream) {
            return;
        }


        try {

            audioContext =
                new (
                    window.AudioContext ||
                    window.webkitAudioContext
                )();


            const source =
                audioContext.createMediaStreamSource(
                    mediaStream
                );


            analyser =
                audioContext.createAnalyser();


            analyser.fftSize =
                2048;


            source.connect(
                analyser
            );


            const data =
                new Uint8Array(
                    analyser.fftSize
                );


            silenceStart =
                null;


            speechDetected =
                false;


            function checkSilence() {

                if (
                    !mediaRecorder ||
                    mediaRecorder.state !==
                        'recording'
                ) {
                    return;
                }


                analyser.getByteTimeDomainData(
                    data
                );


                let sum = 0;


                for (
                    let i = 0;
                    i < data.length;
                    i++
                ) {

                    const normalized =
                        (data[i] - 128) /
                        128;


                    sum +=
                        normalized *
                        normalized;
                }


                const rms =
                    Math.sqrt(
                        sum / data.length
                    );


                // Speech detected.

                if (
                    rms >=
                    SILENCE_THRESHOLD
                ) {

                    speechDetected =
                        true;


                    silenceStart =
                        null;
                }


                // Silence after speech.

                if (
                    speechDetected &&
                    rms <
                        SILENCE_THRESHOLD
                ) {

                    if (
                        silenceStart ===
                        null
                    ) {

                        silenceStart =
                            Date.now();
                    }


                    const silentFor =
                        Date.now() -
                        silenceStart;


                    if (
                        silentFor >=
                        SILENCE_DURATION
                    ) {

                        console.log(
                            'APEX: Speech ended.'
                        );


                        stopRecording();


                        return;
                    }
                }


                silenceCheckId =
                    requestAnimationFrame(
                        checkSilence
                    );
            }


            checkSilence();

        } catch (error) {

            console.warn(
                'Silence detection unavailable:',
                error
            );
        }
    }


    // =========================================================
    // STOP SILENCE DETECTION
    // =========================================================

    function stopSilenceDetection() {

        if (
            silenceCheckId
        ) {

            cancelAnimationFrame(
                silenceCheckId
            );


            silenceCheckId =
                null;
        }


        silenceStart =
            null;


        speechDetected =
            false;


        if (
            audioContext
        ) {

            audioContext
                .close()
                .catch(() => {});


            audioContext =
                null;
        }


        analyser =
            null;
    }


    // =========================================================
    // START RECORDING
    // =========================================================

    async function startRecording() {

        if (
            !isMediaRecorderSupported
        ) {

            showNotification(
                "Audio recording is not supported."
            );


            return;
        }


        // Prevent duplicate sessions.

        if (
            mediaRecorder &&
            mediaRecorder.state ===
                'recording'
        ) {

            return;
        }


        try {

            // Stop APEX speech if necessary.

            if (
                'speechSynthesis' in
                window
            ) {

                window.speechSynthesis.cancel();
            }


            mediaStream =
                await navigator.mediaDevices
                    .getUserMedia({
                        audio: true
                    });


            audioChunks =
                [];


            let options =
                {};


            if (
                MediaRecorder.isTypeSupported(
                    'audio/webm;codecs=opus'
                )
            ) {

                options = {
                    mimeType:
                        'audio/webm;codecs=opus'
                };

            } else if (
                MediaRecorder.isTypeSupported(
                    'audio/webm'
                )
            ) {

                options = {
                    mimeType:
                        'audio/webm'
                };

            } else if (
                MediaRecorder.isTypeSupported(
                    'audio/mp4'
                )
            ) {

                options = {
                    mimeType:
                        'audio/mp4'
                };
            }


            mediaRecorder =
                new MediaRecorder(
                    mediaStream,
                    options
                );


            mediaRecorder.ondataavailable =
                event => {

                    if (
                        event.data &&
                        event.data.size >
                            0
                    ) {

                        audioChunks.push(
                            event.data
                        );
                    }
                };


            mediaRecorder.onstop =
                () => {

                    stopSilenceDetection();


                    const recorder =
                        mediaRecorder;


                    const mimeType =
                        recorder.mimeType ||
                        'audio/webm';


                    const audioBlob =
                        new Blob(
                            audioChunks,
                            {
                                type: mimeType
                            }
                        );


                    // Release microphone.

                    if (
                        mediaStream
                    ) {

                        mediaStream
                            .getTracks()
                            .forEach(
                                track =>
                                    track.stop()
                            );


                        mediaStream =
                            null;
                    }


                    // -------------------------------------------------
                    // OK APEX
                    // -------------------------------------------------

                    if (
                        stopCommandDetected
                    ) {

                        console.log(
                            'APEX: Ignoring recording because ' +
                            'OK APEX was detected.'
                        );


                        stopCommandDetected =
                            false;


                        try {

                            stopRecognition();

                        } catch (error) {

                            // Ignore.
                        }


                        setState(
                            'READY'
                        );


                        return;
                    }


                    if (
                        audioBlob.size ===
                        0
                    ) {

                        showNotification(
                            "No audio recorded. " +
                            "Please try again."
                        );


                        handleResponseFinished();


                        return;
                    }


                    sendVoiceMessage(
                        audioBlob,
                        mimeType
                    );
                };


            mediaRecorder.start();


            stopCommandDetected =
                false;


            recognitionBuffer =
                '';


            // Start local recognition only
            // during Conversation Mode.

            if (
                conversationMode &&
                recognitionSupported
            ) {

                startRecognition();
            }


            setState(
                'LISTENING'
            );


            console.log(
                'APEX: Listening...'
            );


            startSilenceDetection();

        } catch (err) {

            console.error(
                'Microphone access error:',
                err
            );


            let errorMsg =
                "Could not access microphone.";


            if (
                err.name ===
                    'NotAllowedError' ||
                err.name ===
                    'PermissionDeniedError'
            ) {

                errorMsg =
                    "Microphone access denied. " +
                    "Please allow microphone permissions.";

            } else if (
                err.name ===
                'NotFoundError'
            ) {

                errorMsg =
                    "No microphone device found.";
            }


            showNotification(
                errorMsg
            );


            setState(
                'READY'
            );
        }
    }


    // =========================================================
    // STOP RECORDING
    // =========================================================

    function stopRecording() {

        stopSilenceDetection();


        if (
            mediaRecorder &&
            mediaRecorder.state ===
                'recording'
        ) {

            console.log(
                'APEX: Stopping recording.'
            );


            mediaRecorder.stop();
        }
    }


    // =========================================================
    // EMOJI -> SPOKEN MEANING
    // =========================================================
    //
    // The UI keeps the emoji.
    //
    // Example:
    //
    // Screen:
    // "Great job! 😊"
    //
    // Voice:
    // "Great job! Glad."
    //
    // Unknown emojis are removed from speech
    // so the browser does not pronounce their
    // Unicode names.
    //


    const EMOJI_SPOKEN_MEANINGS = {

        // Happiness
        "😊": "glad",
        "🙂": "happy",
        "😀": "happy",
        "😃": "happy",
        "😄": "happy",
        "😁": "happy",

        // Laughing
        "😆": "lol",
        "😂": "lol",
        "🤣": "lol",

        // Positive emotions
        "😅": "relieved",
        "😇": "blessed",
        "🥰": "loving",
        "😍": "love",
        "🤩": "amazed",
        "😎": "cool",
        "🤗": "friendly",
        "🥹": "touched",

        // Thinking / reactions
        "🤔": "thinking",
        "🧐": "curious",
        "🙄": "annoyed",
        "😏": "smirking",
        "🤭": "giggle",
        "🫡": "respect",
        "🫠": "melting",

        // Negative emotions
        "😢": "sad",
        "😭": "crying",
        "😡": "angry",
        "😤": "frustrated",
        "😱": "shocked",
        "😳": "embarrassed",
        "😴": "sleepy",

        // Reactions
        "👍": "thumbs up",
        "👎": "thumbs down",
        "👏": "clap",
        "🙌": "celebration",
        "🙏": "please",
        "🤝": "agreement",
        "💪": "strong",
        "✌️": "peace",
        "👌": "okay",
        "👀": "looking",

        // Love
        "❤️": "love",
        "🩷": "love",
        "💙": "love",
        "💚": "love",
        "💛": "love",
        "🖤": "love",
        "❤️‍🔥": "passion",

        // Common symbols
        "🔥": "fire",
        "⭐": "star",
        "🌟": "great",
        "✨": "sparkle",
        "💯": "perfect",
        "🎯": "target",
        "🚀": "rocket",
        "💡": "idea",
        "🎉": "celebration",
        "🎊": "celebration",
        "🏆": "trophy",
        "✅": "done",
        "❌": "wrong",
        "⚠️": "warning",
        "❗": "important",
        "❓": "question",

        // Funny / casual
        "😜": "playful",
        "😋": "tasty",
        "🤪": "crazy",
        "🤦": "facepalm",
        "🤷": "I don't know",
        "🙈": "shy",
        "💀": "dead laughing",
        "😈": "mischievous"
    };


    function convertEmojiForSpeech(
        text
    ) {

        if (!text) {
            return '';
        }


        let speechText =
            text;


        // Replace known emojis.

        for (
            const [
                emoji,
                meaning
            ]
            of Object.entries(
                EMOJI_SPOKEN_MEANINGS
            )
        ) {

            speechText =
                speechText
                    .split(emoji)
                    .join(
                        ` ${meaning} `
                    );
        }


        // Remove remaining Unicode emojis.
        //
        // This prevents TTS from saying:
        //
        // "rocket"
        // "red heart"
        // "smiling face..."
        //
        // for emojis we don't explicitly support.

        speechText =
            speechText
                .replace(
                    /[\u{1F300}-\u{1FAFF}]/gu,
                    ''
                )
                .replace(
                    /[\u{2600}-\u{27BF}]/gu,
                    ''
                )
                .replace(
                    /\s+/g,
                    ' '
                )
                .trim();


        return speechText;
    }


    // =========================================================
    // TEXT TO SPEECH
    // =========================================================

    function speakResponse(
        text
    ) {

        if (
            !('speechSynthesis' in window)
        ) {

            handleResponseFinished();

            return;
        }


        window.speechSynthesis.cancel();


        // Convert emojis only for speech.
        //
        // The original text remains unchanged
        // in the chat UI.

        const speechText =
            convertEmojiForSpeech(
                text
            );


        const utterance =
            new SpeechSynthesisUtterance(
                speechText
            );


        utterance.rate =
            1.0;


        utterance.pitch =
            1.0;


        const voices =
            window.speechSynthesis
                .getVoices();


        const preferredVoice =
            voices.find(
                voice =>
                    voice.lang.startsWith(
                        'en'
                    ) &&
                    (
                        voice.name.includes(
                            'Google'
                        ) ||
                        voice.name.includes(
                            'Natural'
                        ) ||
                        voice.name.includes(
                            'David'
                        ) ||
                        voice.name.includes(
                            'Zira'
                        )
                    )
            );


        if (
            preferredVoice
        ) {

            utterance.voice =
                preferredVoice;
        }


        utterance.onstart =
            () => {

                setState(
                    'SPEAKING'
                );
            };


        utterance.onend =
            () => {

                handleResponseFinished();
            };


        utterance.onerror =
            () => {

                handleResponseFinished();
            };


        window.speechSynthesis.speak(
            utterance
        );
    }


    // =========================================================
    // CONVERSATION MODE
    // =========================================================

    function startConversationMode() {

        if (
            conversationMode
        ) {

            return;
        }


        conversationMode =
            true;


        stopCommandDetected =
            false;


        recognitionBuffer =
            '';


        // Switch from chat to audio UI.

        setConversationVisualMode(
            true
        );


        showNotification(
            "Conversation mode started. " +
            "Speak naturally; APEX will listen again " +
            "after each response.",
            5000
        );


        setState(
            'READY'
        );


        // Start listening.

        setTimeout(
            () => {

                if (
                    conversationMode
                ) {

                    startRecording();
                }

            },
            600
        );
    }


    function doStopConversationMode(
        options = {}
    ) {

        const {
            suppressNotification = false
        } = options;


        conversationMode =
            false;


        // Stop recognition.

        try {

            stopRecognition();

        } catch (error) {

            // Ignore.
        }


        // Stop recording.

        if (
            mediaRecorder &&
            mediaRecorder.state ===
                'recording'
        ) {

            stopRecording();
        }


        // Stop APEX speech.

        if (
            'speechSynthesis' in
            window
        ) {

            window.speechSynthesis.cancel();
        }


        // Restore chat interface.

        setConversationVisualMode(
            false
        );


        setState(
            'READY'
        );


        if (!suppressNotification) {

            showNotification(
                "Conversation mode ended.",
                2500
            );
        }

    }


    function stopConversationMode() {

        doStopConversationMode({
            suppressNotification: false
        });

    }


    function toggleConversationMode() {

        if (
            conversationMode
        ) {

            stopConversationMode();

        } else {

            startConversationMode();
        }
    }


    // =========================================================
    // MICROPHONE BUTTON
    // =========================================================
    //
    // Important:
    //
    // If APEX is speaking and the user clicks the mic:
    //
    // APEX stops speaking immediately.
    // Conversation Mode remains active.
    // Microphone starts listening immediately.
    //
    // This is the manual "barge-in" feature.
    //


    micBtn.addEventListener(
        'click',
        () => {

            // -------------------------------------------------
            // INTERRUPT APEX WHILE SPEAKING
            // -------------------------------------------------

            if (
                currentState ===
                'SPEAKING'
            ) {

                console.log(
                    'APEX: Speech interrupted by user.'
                );


                if (
                    'speechSynthesis' in
                    window
                ) {

                    window.speechSynthesis.cancel();
                }


                if (
                    conversationMode
                ) {

                    // Start listening immediately.
                    startRecording();

                } else {

                    setState(
                        'READY'
                    );
                }


                return;
            }


            // -------------------------------------------------
            // STOP CURRENT RECORDING
            // -------------------------------------------------

            if (
                currentState ===
                'LISTENING'
            ) {

                stopRecording();

                return;
            }


            // -------------------------------------------------
            // NORMAL MODE
            // -------------------------------------------------

            if (
                currentState ===
                    'READY' &&
                !conversationMode
            ) {

                startRecording();
            }
        }
    );


    // =========================================================
    // CONVERSATION BUTTON
    // =========================================================

    if (
        conversationBtn
    ) {

        conversationBtn.addEventListener(
            'click',
            toggleConversationMode
        );
    }


    // =========================================================
    // TEXT FORM
    // =========================================================

    chatForm.addEventListener(
        'submit',
        event => {

            event.preventDefault();


            const messageText =
                userInput.value.trim();


            if (
                messageText &&
                !isProcessing
            ) {

                if (
                    'speechSynthesis' in
                    window
                ) {

                    window.speechSynthesis.cancel();
                }


                sendTextMessage(
                    messageText
                );
            }
        }
    );


    // =========================================================
    // SPEECH SYNTHESIS VOICES
    // =========================================================

    if (
        'speechSynthesis' in
        window
    ) {

        window.speechSynthesis.onvoiceschanged =
            () =>
                window.speechSynthesis
                    .getVoices();
    }


    // =========================================================
    // INITIALIZE
    // =========================================================

    createConversationVisual();

    setTimeAwareWelcome();

});