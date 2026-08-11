// APEX v0.1 Voice Interface Application
// Phase 5: Tools & Actions
// Features:
// - Time-aware welcome
// - Text chat
// - Voice recording
// - Automatic silence detection
// - Voice transcription through /voice
// - Text-to-speech
// - Tool activity indicators

document.addEventListener('DOMContentLoaded', () => {

    // =========================================================
    // DOM ELEMENTS
    // =========================================================

    const statusContainer = document.querySelector('.status-container');
    const statusText = document.getElementById('statusText');
    const micBtn = document.getElementById('micBtn');
    const chatForm = document.getElementById('chatForm');
    const userInput = document.getElementById('userInput');
    const chatArea = document.getElementById('chatArea');
    const welcomeCard = document.getElementById('welcomeCard');
    const notificationBanner = document.getElementById('notificationBanner');
    const notificationMessage = document.getElementById('notificationMessage');


    // =========================================================
    // STATE
    // =========================================================

    let currentState = 'READY';
    let isProcessing = false;


    // =========================================================
    // TIME-AWARE WELCOME
    // =========================================================

function setTimeAwareWelcome() {
const welcomeTitle = document.getElementById('welcomeTitle');
const welcomeMessage = document.getElementById('welcomeMessage');


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
    greetings[Math.floor(Math.random() * greetings.length)];

// Display greeting
welcomeTitle.textContent = greeting;
welcomeMessage.textContent = "What can I do for you?";

// Speak greeting after the page is ready
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

    // How quiet the microphone must be before we consider it silence.
    const SILENCE_THRESHOLD = 0.015;

    // How long the user must remain silent after speaking.
    const SILENCE_DURATION = 1200;


    // =========================================================
    // MEDIA RECORDER SUPPORT CHECK
    // =========================================================

    const isMediaRecorderSupported = !!(
        navigator.mediaDevices &&
        navigator.mediaDevices.getUserMedia &&
        window.MediaRecorder
    );

    if (!isMediaRecorderSupported) {
        showNotification(
            "Audio recording is not supported in this browser. You can still use text input.",
            8000
        );
    }


    // =========================================================
    // TOOL LABELS
    // =========================================================

    const TOOL_LABELS = {
        "get_current_time": "Checking the time",
        "get_current_date": "Checking the date",
        "open_website": "Opening website",
        "web_search": "Searching the web",
        "calculate": "Calculating",
        "get_system_info": "Reading system info",
        "open_local_app": "Opening application"
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
            statusContainer.className = 'status-container';
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
                micBtn.title = 'Listening... Speak now';
            } else if (newState === 'THINKING') {
                micBtn.title = 'Processing...';
            } else if (newState === 'SPEAKING') {
                micBtn.title = 'Click to stop speaking';
            } else {
                micBtn.title = 'Click to start listening';
            }
        }

        isProcessing = (
            newState === 'THINKING' ||
            newState === 'SPEAKING' ||
            newState === 'LISTENING'
        );
    }


    // =========================================================
    // NOTIFICATION
    // =========================================================

    function showNotification(message, duration = 5000) {
        if (!notificationMessage || !notificationBanner) {
            return;
        }

        notificationMessage.textContent = message;
        notificationBanner.classList.remove('hidden');

        setTimeout(() => {
            notificationBanner.classList.add('hidden');
        }, duration);
    }


    // =========================================================
    // APPEND CHAT MESSAGE
    // =========================================================

    function appendMessage(sender, text) {
        if (welcomeCard) {
            welcomeCard.style.display = 'none';
        }

        const msgBubble = document.createElement('div');

        msgBubble.classList.add(
            'message-bubble',
            sender === 'USER'
                ? 'user-message'
                : 'apex-message'
        );

        const msgHeader = document.createElement('div');
        msgHeader.classList.add('message-header');
        msgHeader.textContent = sender;

        const msgBody = document.createElement('div');
        msgBody.classList.add('message-body');
        msgBody.textContent = text;

        msgBubble.appendChild(msgHeader);
        msgBubble.appendChild(msgBody);

        chatArea.appendChild(msgBubble);
        chatArea.scrollTop = chatArea.scrollHeight;
    }


    // =========================================================
    // TOOL ACTIVITY INDICATOR
    // =========================================================

    function appendToolIndicator(toolName) {
        if (welcomeCard) {
            welcomeCard.style.display = 'none';
        }

        const label = TOOL_LABELS[toolName] || toolName;

        const indicator = document.createElement('div');

        indicator.classList.add('tool-indicator');

        indicator.innerHTML =
            `<span class="tool-icon">⚡</span>` +
            `<span class="tool-label">APEX › ${label}...</span>`;

        indicator.id = 'tool-indicator-active';

        chatArea.appendChild(indicator);
        chatArea.scrollTop = chatArea.scrollHeight;
    }


    function removeToolIndicator() {
        const el = document.getElementById(
            'tool-indicator-active'
        );

        if (el) {
            el.remove();
        }
    }


    // =========================================================
    // SEND TEXT MESSAGE
    // =========================================================

    async function sendTextMessage(text) {
        if (!text || text.trim() === '') {
            return;
        }

        appendMessage('USER', text);

        userInput.value = '';

        setState('THINKING');

        try {
            const response = await fetch('/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    message: text
                })
            });

            const data = await response.json();

            if (data.tool_used) {
                appendToolIndicator(data.tool_used);

                await new Promise(resolve =>
                    setTimeout(resolve, 400)
                );

                removeToolIndicator();
            }

            if (data.success) {
                appendMessage('APEX', data.response);

                speakResponse(data.response);

            } else if (data.error_type === 'QUOTA_EXHAUSTED') {

                const msg =
                    "Oops, my AI brain has hit today's free quota 😅. " +
                    "My built-in tools are still available! Try: " +
                    "'What time is it?', 'Open YouTube', or " +
                    "'Calculate 25 * 17'.";

                appendMessage('APEX', msg);

                showNotification(
                    "AI quota exhausted. Built-in tools still work!",
                    7000
                );

                setState('READY');

            } else {

                const errorText =
                    data.error ||
                    'Failed to get response from APEX.';

                appendMessage(
                    'APEX',
                    `[Error]: ${errorText}`
                );

                showNotification(errorText);

                setState('READY');
            }

        } catch (err) {

            console.error('Fetch error:', err);

            appendMessage(
                'APEX',
                '[Error]: Network error. Could not connect to APEX backend server.'
            );

            showNotification(
                'Network error. Could not connect to APEX backend.'
            );

            setState('READY');
        }
    }


    // =========================================================
    // SEND VOICE MESSAGE
    // =========================================================

    async function sendVoiceMessage(audioBlob, mimeType) {
        setState('THINKING');

        const formData = new FormData();

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
            const response = await fetch('/voice', {
                method: 'POST',
                body: formData
            });

            const data = await response.json();

            if (data.tool_used) {
                appendToolIndicator(data.tool_used);

                await new Promise(resolve =>
                    setTimeout(resolve, 400)
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

                speakResponse(data.response);

            } else if (data.error_type === 'QUOTA_EXHAUSTED') {

                const msg =
                    "Oops, my AI brain has hit today's free quota 😅. " +
                    "My built-in tools are still available! " +
                    "Try asking via text: 'What time is it?' " +
                    "or 'Open YouTube'.";

                appendMessage('APEX', msg);

                showNotification(
                    "AI quota exhausted. Text-based tools still work!",
                    7000
                );

                setState('READY');

            } else {

                const errorText =
                    data.error ||
                    'Failed to process voice input.';

                showNotification(errorText);

                setState('READY');
            }

        } catch (err) {

            console.error('Voice upload error:', err);

            showNotification(
                'Network error while sending audio to APEX backend.'
            );

            setState('READY');
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

            analyser = audioContext.createAnalyser();

            analyser.fftSize = 2048;

            source.connect(analyser);

            const data =
                new Uint8Array(analyser.fftSize);

            silenceStart = null;
            speechDetected = false;

            function checkSilence() {

                if (
                    !mediaRecorder ||
                    mediaRecorder.state !== 'recording'
                ) {
                    return;
                }

                analyser.getByteTimeDomainData(data);

                let sum = 0;

                for (let i = 0; i < data.length; i++) {

                    const normalized =
                        (data[i] - 128) / 128;

                    sum += normalized * normalized;
                }

                const rms =
                    Math.sqrt(sum / data.length);


                // -----------------------------------------
                // Speech detected
                // -----------------------------------------

                if (rms >= SILENCE_THRESHOLD) {

                    if (!speechDetected) {
                        console.log(
                            'APEX: Speech detected.'
                        );
                    }

                    speechDetected = true;

                    silenceStart = null;
                }


                // -----------------------------------------
                // Silence AFTER speech
                // -----------------------------------------

                if (
                    speechDetected &&
                    rms < SILENCE_THRESHOLD
                ) {

                    if (silenceStart === null) {
                        silenceStart = Date.now();
                    }

                    const silentFor =
                        Date.now() - silenceStart;

                    if (
                        silentFor >= SILENCE_DURATION
                    ) {

                        console.log(
                            'APEX: Speech ended. Stopping recording.'
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
                "Audio recording is not supported in this browser."
            );

            return;
        }

        try {

            // Stop any current speech synthesis.
            if ('speechSynthesis' in window) {
                window.speechSynthesis.cancel();
            }


            // Ask for microphone access.
            mediaStream =
                await navigator.mediaDevices.getUserMedia({
                    audio: true
                });


            audioChunks = [];


            // Select supported recording format.
            let options = {};

            if (
                MediaRecorder.isTypeSupported(
                    'audio/webm;codecs=opus'
                )
            ) {

                options = {
                    mimeType: 'audio/webm;codecs=opus'
                };

            } else if (
                MediaRecorder.isTypeSupported(
                    'audio/webm'
                )
            ) {

                options = {
                    mimeType: 'audio/webm'
                };

            } else if (
                MediaRecorder.isTypeSupported(
                    'audio/mp4'
                )
            ) {

                options = {
                    mimeType: 'audio/mp4'
                };
            }


            mediaRecorder =
                new MediaRecorder(
                    mediaStream,
                    options
                );


            // -----------------------------------------
            // Audio data
            // -----------------------------------------

            mediaRecorder.ondataavailable =
                (event) => {

                    if (
                        event.data &&
                        event.data.size > 0
                    ) {

                        audioChunks.push(
                            event.data
                        );
                    }
                };


            // -----------------------------------------
            // Recording stopped
            // -----------------------------------------

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


                // Release microphone.
                if (mediaStream) {

                    mediaStream
                        .getTracks()
                        .forEach(track =>
                            track.stop()
                        );

                    mediaStream = null;
                }


                // Empty recording check.
                if (audioBlob.size === 0) {

                    showNotification(
                        "No audio recorded. Please try again."
                    );

                    setState('READY');

                    return;
                }


                // Send recording to backend.
                sendVoiceMessage(
                    audioBlob,
                    mimeType
                );
            };


            // -----------------------------------------
            // Start recording
            // -----------------------------------------

            mediaRecorder.start();

            setState('LISTENING');

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
                err.name === 'NotAllowedError' ||
                err.name === 'PermissionDeniedError'
            ) {

                errorMsg =
                    "Microphone access denied. " +
                    "Please allow microphone permissions " +
                    "in your browser.";

            } else if (
                err.name === 'NotFoundError'
            ) {

                errorMsg =
                    "No microphone device found " +
                    "on your system.";
            }


            showNotification(errorMsg);

            setState('READY');
        }
    }


    // =========================================================
    // STOP RECORDING
    // =========================================================

    function stopRecording() {

        stopSilenceDetection();

        if (
            mediaRecorder &&
            mediaRecorder.state === 'recording'
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

            setState('READY');

            return;
        }


        window.speechSynthesis.cancel();


        const utterance =
            new SpeechSynthesisUtterance(text);

        utterance.rate = 1.0;
        utterance.pitch = 1.0;


        const voices =
            window.speechSynthesis.getVoices();


        const preferredVoice =
            voices.find(voice =>
                voice.lang.startsWith('en') &&
                (
                    voice.name.includes('Google') ||
                    voice.name.includes('Natural') ||
                    voice.name.includes('David') ||
                    voice.name.includes('Zira')
                )
            );


        if (preferredVoice) {
            utterance.voice = preferredVoice;
        }


        utterance.onstart = () => {
            setState('SPEAKING');
        };


        utterance.onend = () => {
            setState('READY');
        };


        utterance.onerror = () => {
            setState('READY');
        };


        window.speechSynthesis.speak(
            utterance
        );
    }


    // =========================================================
    // MICROPHONE BUTTON
    // =========================================================

    micBtn.addEventListener(
        'click',
        () => {

            if (currentState === 'READY') {

                startRecording();

            } else if (
                currentState === 'LISTENING'
            ) {

                // Manual stop still works.
                stopRecording();

            } else if (
                currentState === 'SPEAKING'
            ) {

                if (
                    'speechSynthesis' in window
                ) {

                    window.speechSynthesis.cancel();
                }

                setState('READY');
            }
        }
    );


    // =========================================================
    // TEXT FORM
    // =========================================================

    chatForm.addEventListener(
        'submit',
        (event) => {

            event.preventDefault();

            const messageText =
                userInput.value.trim();


            if (
                messageText &&
                !isProcessing
            ) {

                if (
                    'speechSynthesis' in window
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
        'speechSynthesis' in window
    ) {

        window.speechSynthesis.onvoiceschanged =
            () =>
                window.speechSynthesis.getVoices();
    }


    // =========================================================
    // INITIALIZE TIME-AWARE WELCOME
    // =========================================================

    setTimeAwareWelcome();

});