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
// - Conversation Mode: listen -> process -> speak -> listen again

document.addEventListener('DOMContentLoaded', () => {

    // =========================================================
    // DOM ELEMENTS
    // =========================================================

    const statusContainer = document.querySelector('.status-container');
    const statusText = document.getElementById('statusText');

    const micBtn = document.getElementById('micBtn');
    const conversationBtn =
        document.getElementById('conversationBtn');

    const chatForm = document.getElementById('chatForm');
    const userInput = document.getElementById('userInput');

    const chatArea = document.getElementById('chatArea');
    const welcomeCard = document.getElementById('welcomeCard');

    const notificationBanner =
        document.getElementById('notificationBanner');

    const notificationMessage =
        document.getElementById('notificationMessage');


    // =========================================================
    // STATE
    // =========================================================

    let currentState = 'READY';
    let isProcessing = false;

    // Conversation mode
    let conversationMode = false;


    // =========================================================
    // TIME-AWARE WELCOME
    // =========================================================

    function setTimeAwareWelcome() {

        const welcomeTitle =
            document.getElementById('welcomeTitle');

        const welcomeMessage =
            document.getElementById('welcomeMessage');

        if (!welcomeTitle || !welcomeMessage) {
            return;
        }

        const hour = new Date().getHours();

        let greetings;

        if (hour >= 5 && hour < 12) {

            greetings = [
                "Good morning, Boss. What's on your mind?",
                "Morning, Boss. What can I do for you?",
                "Good morning, Boss. Ready to get things moving?"
            ];

        } else if (hour >= 12 && hour < 17) {

            greetings = [
                "Good afternoon, Boss. How's your day going?",
                "Good afternoon, Boss. What are we working on?",
                "Hey Boss, how can I help you?"
            ];

        } else if (hour >= 17 && hour < 21) {

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
                    Math.random() * greetings.length
                )
            ];

        welcomeTitle.textContent = greeting;
        welcomeMessage.textContent =
            "What can I do for you?";

        // Speak the greeting after a short delay.
        if ('speechSynthesis' in window) {

            setTimeout(() => {

                speakResponse(greeting);

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

    const SILENCE_THRESHOLD = 0.015;

    const SILENCE_DURATION = 1200;


    // =========================================================
    // MEDIA RECORDER SUPPORT
    // =========================================================

    const isMediaRecorderSupported = !!(
        navigator.mediaDevices &&
        navigator.mediaDevices.getUserMedia &&
        window.MediaRecorder
    );

    if (!isMediaRecorderSupported) {

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

    function setState(newState) {

        currentState = newState;

        if (statusText) {
            statusText.textContent = newState;
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
                newState === 'LISTENING'
            );

            if (newState === 'LISTENING') {

                micBtn.title =
                    conversationMode
                        ? 'Listening...'
                        : 'Listening... Speak now';

            } else if (newState === 'THINKING') {

                micBtn.title =
                    'Processing...';

            } else if (newState === 'SPEAKING') {

                micBtn.title =
                    'Click to interrupt';

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


        // Update conversation button.

        if (conversationBtn) {

            if (conversationMode) {

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
            welcomeCard.style.display = 'none';
        }

        const msgBubble =
            document.createElement('div');

        msgBubble.classList.add(
            'message-bubble',
            sender === 'USER'
                ? 'user-message'
                : 'apex-message'
        );

        const msgHeader =
            document.createElement('div');

        msgHeader.classList.add(
            'message-header'
        );

        msgHeader.textContent = sender;

        const msgBody =
            document.createElement('div');

        msgBody.classList.add(
            'message-body'
        );

        msgBody.textContent = text;

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
            welcomeCard.style.display = 'none';
        }

        const label =
            TOOL_LABELS[toolName] ||
            toolName;

        const indicator =
            document.createElement('div');

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

        userInput.value = '';

        setState('THINKING');

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


            if (data.tool_used) {

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


            if (data.success) {

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

        setState('THINKING');

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


            if (data.tool_used) {

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


            if (data.success) {

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
                'Network error while sending audio to APEX backend.'
            );

            handleResponseFinished();
        }
    }


    // =========================================================
    // RESPONSE FINISHED
    // =========================================================

    function handleResponseFinished() {

        setState('READY');

        if (conversationMode) {

            // Small delay so the user gets a natural pause
            // before APEX starts listening again.

            setTimeout(() => {

                if (
                    conversationMode &&
                    currentState === 'READY'
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

            analyser.fftSize = 2048;

            source.connect(
                analyser
            );

            const data =
                new Uint8Array(
                    analyser.fftSize
                );

            silenceStart = null;
            speechDetected = false;


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
                        (data[i] - 128) / 128;

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

                    speechDetected = true;

                    silenceStart = null;
                }


                // Silence AFTER speech.

                if (
                    speechDetected &&
                    rms <
                        SILENCE_THRESHOLD
                ) {

                    if (
                        silenceStart === null
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

        if (silenceCheckId) {

            cancelAnimationFrame(
                silenceCheckId
            );

            silenceCheckId = null;
        }


        silenceStart = null;

        speechDetected = false;


        if (audioContext) {

            audioContext
                .close()
                .catch(() => {});

            audioContext = null;
        }


        analyser = null;
    }


    // =========================================================
    // START RECORDING
    // =========================================================

    async function startRecording() {

        if (!isMediaRecorderSupported) {

            showNotification(
                "Audio recording is not supported."
            );

            return;
        }


        // Prevent duplicate microphone sessions.

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


            audioChunks = [];


            let options = {};


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
                        event.data.size > 0
                    ) {

                        audioChunks.push(
                            event.data
                        );
                    }
                };


            mediaRecorder.onstop = () => {

                stopSilenceDetection();


                const mimeType =
                    mediaRecorder.mimeType ||
                    'audio/webm';


                const audioBlob =
                    new Blob(
                        audioChunks,
                        {
                            type: mimeType
                        }
                    );


                if (mediaStream) {

                    mediaStream
                        .getTracks()
                        .forEach(
                            track =>
                                track.stop()
                        );

                    mediaStream = null;
                }


                if (
                    audioBlob.size === 0
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
    // TEXT TO SPEECH
    // =========================================================

    function speakResponse(text) {

        if (
            !('speechSynthesis' in window)
        ) {

            handleResponseFinished();

            return;
        }


        window.speechSynthesis.cancel();


        const utterance =
            new SpeechSynthesisUtterance(
                text
            );

        utterance.rate = 1.0;
        utterance.pitch = 1.0;


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


        if (preferredVoice) {

            utterance.voice =
                preferredVoice;
        }


        utterance.onstart = () => {

            setState(
                'SPEAKING'
            );
        };


        utterance.onend = () => {

            handleResponseFinished();
        };


        utterance.onerror = () => {

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

        if (conversationMode) {
            return;
        }


        conversationMode = true;


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

        setTimeout(() => {

            if (conversationMode) {
                startRecording();
            }

        }, 600);
    }


    function stopConversationMode() {

        conversationMode = false;


        // Stop recording if active.

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


        setState(
            'READY'
        );


        showNotification(
            "Conversation mode ended.",
            2500
        );
    }


    function toggleConversationMode() {

        if (conversationMode) {

            stopConversationMode();

        } else {

            startConversationMode();
        }
    }


    // =========================================================
    // MICROPHONE BUTTON
    // =========================================================

    micBtn.addEventListener(
        'click',
        () => {

            // If APEX is speaking,
            // clicking the microphone interrupts it.

            if (
                currentState ===
                'SPEAKING'
            ) {

                if (
                    'speechSynthesis' in
                    window
                ) {

                    window.speechSynthesis.cancel();
                }


                if (
                    conversationMode
                ) {

                    setTimeout(
                        () =>
                            startRecording(),
                        300
                    );

                } else {

                    setState(
                        'READY'
                    );
                }


                return;
            }


            // If currently listening,
            // clicking microphone manually stops it.

            if (
                currentState ===
                'LISTENING'
            ) {

                stopRecording();

                return;
            }


            // Normal mode.

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

    if (conversationBtn) {

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

    setTimeAwareWelcome();

});

