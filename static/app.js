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

    const appContainer =
        document.getElementById('appContainer');

    const activityLog =
        document.getElementById('activityLog');

    const toolsList =
        document.getElementById('toolsList');

    const apexStatusDot =
        document.getElementById('apexStatusDot');

    const apexStatusLabel =
        document.getElementById('apexStatusLabel');

    const apexStatusDetail =
        document.getElementById('apexStatusDetail');

    const apexStatusPanel =
        document.querySelector('.panel-apex-status');

    const micLabel =
        document.getElementById('micLabel');

    const headerVoiceStatus =
        document.getElementById('headerVoiceStatus');

    const sysVoice =
        document.getElementById('sysVoice');

    const sysVoiceBar =
        document.getElementById('sysVoiceBar');

    const systemFooterTime =
        document.getElementById('systemFooterTime');

    const welcomeTime =
        document.getElementById('welcomeTime');


    // =========================================================
    // TERMINAL DASHBOARD HELPERS
    // =========================================================

    function formatTime(
        date = new Date()
    ) {

        return date.toLocaleTimeString(
            'en-US',
            {
                hour12: false
            }
        );
    }


    function logActivity(
        source,
        message,
        sourceClass = 'system'
    ) {

        if (
            !activityLog ||
            !message
        ) {
            return;
        }


        const entry =
            document.createElement('div');


        entry.className =
            'activity-entry';


        const timeSpan =
            document.createElement('span');


        timeSpan.className =
            'activity-time';


        timeSpan.textContent =
            `[${formatTime()}]`;


        const sourceSpan =
            document.createElement('span');


        sourceSpan.className =
            `activity-source activity-source-${sourceClass}`;


        sourceSpan.textContent =
            source;


        const msgSpan =
            document.createElement('span');


        msgSpan.className =
            'activity-msg';


        msgSpan.textContent =
            message;


        entry.appendChild(timeSpan);
        entry.appendChild(sourceSpan);
        entry.appendChild(msgSpan);


        activityLog.appendChild(
            entry
        );


        while (
            activityLog.children.length >
            200
        ) {

            activityLog.removeChild(
                activityLog.firstChild
            );
        }


        activityLog.scrollTop =
            activityLog.scrollHeight;
    }


    function initToolsList() {

        if (
            !toolsList
        ) {
            return;
        }


        toolsList.innerHTML =
            '';


        Object.keys(
            TOOL_LABELS
        ).forEach(
            toolName => {

                const item =
                    document.createElement(
                        'li'
                    );


                item.className =
                    'tool-item tool-item-idle';


                item.dataset.tool =
                    toolName;


                item.textContent =
                    toolName;


                toolsList.appendChild(
                    item
                );
            }
        );
    }


    function updateToolStatus(
        toolName,
        status
    ) {

        if (
            !toolsList ||
            !toolName
        ) {
            return;
        }


        const items =
            toolsList.querySelectorAll(
                '.tool-item'
            );


        items.forEach(
            item => {

                item.classList.remove(
                    'tool-item-active',
                    'tool-item-done',
                    'tool-item-idle'
                );


                if (
                    item.dataset.tool ===
                    toolName
                ) {

                    item.classList.add(
                        status === 'active'
                            ? 'tool-item-active'
                            : status === 'done'
                                ? 'tool-item-done'
                                : 'tool-item-idle'
                    );

                } else {

                    item.classList.add(
                        'tool-item-idle'
                    );
                }
            }
        );
    }


    function resetToolStatus() {

        if (
            !toolsList
        ) {
            return;
        }


        toolsList.querySelectorAll(
            '.tool-item'
        ).forEach(
            item => {

                item.classList.remove(
                    'tool-item-active',
                    'tool-item-done'
                );

                item.classList.add(
                    'tool-item-idle'
                );
            }
        );
    }


    function updateApexStatusPanel(
        state
    ) {

        const labels = {
            READY: 'READY',
            LISTENING: 'LISTENING',
            THINKING: 'PROCESSING',
            SPEAKING: 'SPEAKING'
        };


        const details = {
            READY: 'Waiting for input...',
            LISTENING: 'Microphone active...',
            THINKING: 'APEX is processing...',
            SPEAKING: 'Voice output active...'
        };


        if (
            apexStatusLabel
        ) {

            apexStatusLabel.textContent =
                labels[state] ||
                state;
        }


        if (
            apexStatusDetail
        ) {

            apexStatusDetail.textContent =
                details[state] ||
                '';
        }


        if (
            apexStatusPanel
        ) {

            apexStatusPanel.classList.remove(
                'apex-ready',
                'apex-listening',
                'apex-thinking',
                'apex-speaking'
            );


            apexStatusPanel.classList.add(
                `apex-${state.toLowerCase()}`
            );
        }
    }


    function updateSystemPanels(
        state
    ) {

        const voiceLabels = {
            READY: 'READY',
            LISTENING: 'LISTENING',
            THINKING: 'BUSY',
            SPEAKING: 'SPEAKING'
        };


        if (
            sysVoice
        ) {

            sysVoice.textContent =
                voiceLabels[state] ||
                state;
        }


        if (
            sysVoiceBar
        ) {

            sysVoiceBar.className =
                'status-fill';


            if (
                state ===
                'LISTENING'
            ) {

                sysVoiceBar.classList.add(
                    'fill-active'
                );

            } else if (
                state ===
                'THINKING'
            ) {

                sysVoiceBar.classList.add(
                    'fill-processing'
                );

            } else if (
                state ===
                'SPEAKING'
            ) {

                sysVoiceBar.classList.add(
                    'fill-speaking'
                );

            } else {

                sysVoiceBar.classList.add(
                    'fill-full'
                );
            }
        }


        if (
            headerVoiceStatus
        ) {

            headerVoiceStatus.textContent =
                `VOICE: ${voiceLabels[state] || state}`;
        }
    }


    function updateMicLabel(
        state
    ) {

        if (
            !micLabel
        ) {
            return;
        }


        if (
            state ===
            'LISTENING'
        ) {

            micLabel.textContent =
                '■ STOP';

            micBtn.classList.add(
                'listening'
            );

            micBtn.classList.remove(
                'speaking'
            );

        } else if (
            state ===
            'SPEAKING'
        ) {

            micLabel.textContent =
                '◼ INTERRUPT';

            micBtn.classList.add(
                'speaking'
            );

            micBtn.classList.remove(
                'listening'
            );

        } else {

            micLabel.textContent =
                '🎙 LISTEN';

            micBtn.classList.remove(
                'listening',
                'speaking'
            );
        }
    }


    function updateSystemFooterTime() {

        if (
            systemFooterTime
        ) {

            systemFooterTime.textContent =
                formatTime();
        }
    }


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

        // Terminal dashboard uses the main layout.
        // Keep this hook for compatibility with existing flow.
        conversationVisual =
            document.getElementById(
                'activityLog'
            );
    }


    function updateConversationVisualState(
        state
    ) {

        updateApexStatusPanel(
            state
        );
    }


    function setConversationVisualMode(
        enabled
    ) {

        createConversationVisual();


        if (
            appContainer
        ) {

            appContainer.classList.toggle(
                'conversation-mode',
                enabled
            );
        }


        if (enabled) {

            logActivity(
                'SYSTEM',
                'Conversation mode initialized.',
                'system'
            );


            logActivity(
                'SYSTEM',
                'Voice channel ready.',
                'system'
            );

        } else {

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
            welcomeTime
        ) {

            welcomeTime.textContent =
                `[${formatTime()}]`;
        }


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
    // API base configuration (development proxy-friendly)
    // =========================================================

    // Resolve API base using (in order):
    // 1) <meta name="apex-api-base" content="http://127.0.0.1:8000">
    // 2) If running on Live Server default port 5500, point to local FastAPI on 8000
    // 3) Otherwise use same-origin (empty string, so fetch('/chat') still works)
    const APEX_API_BASE = (() => {
        try {
            const m = document.querySelector('meta[name="apex-api-base"]');
            if (m && m.content) {
                return m.content.replace(/\/$/, '');
            }
        } catch (e) {}

        // Detect common Live Server port used during local dev
        if (location.hostname === '127.0.0.1' || location.hostname === 'localhost') {
            if (location.port === '5500') {
                return 'http://127.0.0.1:8000';
            }
        }

        return '';
    })();

    function buildApiUrl(path) {
        // path must start with '/'
        if (!path.startsWith('/')) path = '/' + path;
        return (APEX_API_BASE || '') + path;
    }


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
                'ONLINE';
        }


        if (statusContainer) {

            statusContainer.className =
                'header-status status-container status-online';
        }


        updateApexStatusPanel(
            newState
        );


        updateSystemPanels(
            newState
        );


        updateMicLabel(
            newState
        );


        if (
            newState ===
            'LISTENING'
        ) {

            logActivity(
                'SYSTEM',
                'Microphone active.',
                'system'
            );

        } else if (
            newState ===
            'THINKING'
        ) {

            logActivity(
                'APEX',
                'Processing request...',
                'apex'
            );

        } else if (
            newState ===
            'SPEAKING'
        ) {

            logActivity(
                'SYSTEM',
                'Voice output active.',
                'system'
            );
        }


        if (micBtn) {

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
                    'EXIT';


                conversationBtn.classList.add(
                    'conversation-active'
                );


                conversationBtn.title =
                    'Stop conversation mode';

                conversationBtn.setAttribute(
                    'aria-label',
                    'Exit conversation mode'
                );

            } else {

                conversationBtn.textContent =
                    'CONV';


                conversationBtn.classList.remove(
                    'conversation-active'
                );


                conversationBtn.title =
                    'Start conversation mode';

                conversationBtn.setAttribute(
                    'aria-label',
                    'Start conversation mode'
                );
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
            `[${formatTime()}] ${sender === 'USER' ? 'YOU' : sender}`;


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


        const activitySource =
            sender === 'USER'
                ? 'USER'
                : 'APEX';


        const activityClass =
            sender === 'USER'
                ? 'user'
                : 'apex';


        logActivity(
            activitySource,
            text,
            activityClass
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


        const indicator =
            document.createElement(
                'div'
            );


        indicator.classList.add(
            'tool-indicator'
        );


        const icon = document.createElement('span');
        icon.className = 'tool-icon';
        icon.textContent = '⚡';
        const labelSpan = document.createElement('span');
        labelSpan.className = 'tool-label';
        labelSpan.textContent =
            toolName;


        indicator.appendChild(icon);
        indicator.appendChild(labelSpan);


        indicator.id =
            'tool-indicator-active';


        updateToolStatus(
            toolName,
            'active'
        );


        logActivity(
            'TOOL',
            toolName,
            'tool'
        );


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

            const toolName =
                element.querySelector(
                    '.tool-label'
                )?.textContent;


            if (
                toolName
            ) {

                updateToolStatus(
                    toolName,
                    'done'
                );


                setTimeout(
                    resetToolStatus,
                    2000
                );
            }


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
                    buildApiUrl('/chat'),
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
                    buildApiUrl('/voice'),
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

    initToolsList();

    updateApexStatusPanel(
        'READY'
    );

    updateSystemPanels(
        'READY'
    );

    updateMicLabel(
        'READY'
    );

    updateSystemFooterTime();

    setInterval(
        updateSystemFooterTime,
        1000
    );

    logActivity(
        'SYSTEM',
        'APEX initialized.',
        'system'
    );

    setTimeAwareWelcome();

});