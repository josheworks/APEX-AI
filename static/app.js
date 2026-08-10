// APEX v0.1 Voice Interface Application — Phase 5: Tools & Actions

document.addEventListener('DOMContentLoaded', () => {
    // DOM Elements
    const statusContainer = document.querySelector('.status-container');
    const statusText = document.getElementById('statusText');
    const micBtn = document.getElementById('micBtn');
    const chatForm = document.getElementById('chatForm');
    const userInput = document.getElementById('userInput');
    const chatArea = document.getElementById('chatArea');
    const welcomeCard = document.getElementById('welcomeCard');
    const notificationBanner = document.getElementById('notificationBanner');
    const notificationMessage = document.getElementById('notificationMessage');

    // State Variables
    let currentState = 'READY';
    let isProcessing = false;

    // MediaRecorder Variables
    let mediaRecorder = null;
    let audioChunks = [];
    let mediaStream = null;

    const isMediaRecorderSupported = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia && window.MediaRecorder);
    if (!isMediaRecorderSupported) {
        showNotification("Audio recording is not supported in this browser. You can still use text input.", 8000);
    }

    // Friendly tool name labels
    const TOOL_LABELS = {
        "get_current_time": "Checking the time",
        "get_current_date": "Checking the date",
        "open_website": "Opening website",
        "web_search": "Searching the web",
        "calculate": "Calculating",
        "get_system_info": "Reading system info",
        "open_local_app": "Opening application"
    };

    // State Machine Manager
    function setState(newState) {
        currentState = newState;
        statusText.textContent = newState;
        statusContainer.className = 'status-container';
        statusContainer.classList.add(`status-${newState.toLowerCase()}`);
        micBtn.classList.toggle('listening', newState === 'LISTENING');
        isProcessing = (newState === 'THINKING' || newState === 'SPEAKING' || newState === 'LISTENING');
    }

    // Show Notification Banner
    function showNotification(message, duration = 5000) {
        notificationMessage.textContent = message;
        notificationBanner.classList.remove('hidden');
        setTimeout(() => notificationBanner.classList.add('hidden'), duration);
    }

    // Append Message to Conversation Area
    function appendMessage(sender, text) {
        if (welcomeCard) welcomeCard.style.display = 'none';

        const msgBubble = document.createElement('div');
        msgBubble.classList.add('message-bubble', sender === 'USER' ? 'user-message' : 'apex-message');

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

    // Append Tool Activity Indicator
    function appendToolIndicator(toolName) {
        if (welcomeCard) welcomeCard.style.display = 'none';
        const label = TOOL_LABELS[toolName] || toolName;

        const indicator = document.createElement('div');
        indicator.classList.add('tool-indicator');
        indicator.innerHTML = `<span class="tool-icon">⚡</span><span class="tool-label">APEX › ${label}...</span>`;
        indicator.id = 'tool-indicator-active';
        chatArea.appendChild(indicator);
        chatArea.scrollTop = chatArea.scrollHeight;
    }

    // Remove Tool Activity Indicator
    function removeToolIndicator() {
        const el = document.getElementById('tool-indicator-active');
        if (el) el.remove();
    }

    // Send Text Message to /chat Endpoint
    async function sendTextMessage(text) {
        if (!text || text.trim() === '') return;

        appendMessage('USER', text);
        userInput.value = '';
        setState('THINKING');

        try {
            const response = await fetch('/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: text })
            });

            const data = await response.json();

            if (data.tool_used) {
                appendToolIndicator(data.tool_used);
                // Brief pause so user sees the indicator
                await new Promise(r => setTimeout(r, 400));
                removeToolIndicator();
            }

            if (data.success) {
                appendMessage('APEX', data.response);
                speakResponse(data.response);
            } else if (data.error_type === 'QUOTA_EXHAUSTED') {
                const msg = "Oops, my AI brain has hit today's free quota 😅. My built-in tools are still available! Try: 'What time is it?', 'Open YouTube', or 'Calculate 25 * 17'.";
                appendMessage('APEX', msg);
                showNotification("AI quota exhausted. Built-in tools still work!", 7000);
                setState('READY');
            } else {
                const errorText = data.error || 'Failed to get response from APEX.';
                appendMessage('APEX', `[Error]: ${errorText}`);
                showNotification(errorText);
                setState('READY');
            }
        } catch (err) {
            console.error('Fetch error:', err);
            appendMessage('APEX', '[Error]: Network error. Could not connect to APEX backend server.');
            showNotification('Network error. Could not connect to APEX backend.');
            setState('READY');
        }
    }

    // Send Recorded Audio to /voice Endpoint
    async function sendVoiceMessage(audioBlob, mimeType) {
        setState('THINKING');

        const formData = new FormData();
        const extension = mimeType.includes('mp4') ? 'mp4' : 'webm';
        formData.append('file', audioBlob, `recording.${extension}`);

        try {
            const response = await fetch('/voice', {
                method: 'POST',
                body: formData
            });

            const data = await response.json();

            if (data.tool_used) {
                appendToolIndicator(data.tool_used);
                await new Promise(r => setTimeout(r, 400));
                removeToolIndicator();
            }

            if (data.success) {
                appendMessage('USER', data.transcription);
                appendMessage('APEX', data.response);
                speakResponse(data.response);
            } else if (data.error_type === 'QUOTA_EXHAUSTED') {
                const msg = "Oops, my AI brain has hit today's free quota 😅. My built-in tools are still available! Try asking via text: 'What time is it?' or 'Open YouTube'.";
                appendMessage('APEX', msg);
                showNotification("AI quota exhausted. Text-based tools still work!", 7000);
                setState('READY');
            } else {
                const errorText = data.error || 'Failed to process voice input.';
                showNotification(errorText);
                setState('READY');
            }
        } catch (err) {
            console.error('Voice upload error:', err);
            showNotification('Network error while sending audio to APEX backend.');
            setState('READY');
        }
    }

    // Start Recording Microphone Audio
    async function startRecording() {
        if (!isMediaRecorderSupported) {
            showNotification("Audio recording is not supported in this browser.");
            return;
        }
        try {
            if ('speechSynthesis' in window) window.speechSynthesis.cancel();
            mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
            audioChunks = [];

            let options = {};
            if (MediaRecorder.isTypeSupported('audio/webm;codecs=opus')) {
                options = { mimeType: 'audio/webm;codecs=opus' };
            } else if (MediaRecorder.isTypeSupported('audio/webm')) {
                options = { mimeType: 'audio/webm' };
            } else if (MediaRecorder.isTypeSupported('audio/mp4')) {
                options = { mimeType: 'audio/mp4' };
            }

            mediaRecorder = new MediaRecorder(mediaStream, options);

            mediaRecorder.ondataavailable = (event) => {
                if (event.data && event.data.size > 0) audioChunks.push(event.data);
            };

            mediaRecorder.onstop = () => {
                const mimeType = mediaRecorder.mimeType || 'audio/webm';
                const audioBlob = new Blob(audioChunks, { type: mimeType });
                if (mediaStream) mediaStream.getTracks().forEach(t => t.stop());

                if (audioBlob.size === 0) {
                    showNotification("No audio recorded. Please try again.");
                    setState('READY');
                    return;
                }
                sendVoiceMessage(audioBlob, mimeType);
            };

            mediaRecorder.start();
            setState('LISTENING');
        } catch (err) {
            console.error('Microphone access error:', err);
            let errorMsg = "Could not access microphone.";
            if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') {
                errorMsg = "Microphone access denied. Please allow microphone permissions in your browser.";
            } else if (err.name === 'NotFoundError') {
                errorMsg = "No microphone device found on your system.";
            }
            showNotification(errorMsg);
            setState('READY');
        }
    }

    function stopRecording() {
        if (mediaRecorder && mediaRecorder.state === 'recording') {
            mediaRecorder.stop();
        }
    }

    // Text-to-Speech (TTS) Engine
    function speakResponse(text) {
        if (!('speechSynthesis' in window)) { setState('READY'); return; }
        window.speechSynthesis.cancel();

        const utterance = new SpeechSynthesisUtterance(text);
        utterance.rate = 1.0;
        utterance.pitch = 1.0;

        const voices = window.speechSynthesis.getVoices();
        const preferredVoice = voices.find(v =>
            v.lang.startsWith('en') &&
            (v.name.includes('Google') || v.name.includes('Natural') || v.name.includes('David') || v.name.includes('Zira'))
        );
        if (preferredVoice) utterance.voice = preferredVoice;

        utterance.onstart = () => setState('SPEAKING');
        utterance.onend = () => setState('READY');
        utterance.onerror = () => setState('READY');

        window.speechSynthesis.speak(utterance);
    }

    // Microphone Button (Push-to-Talk)
    micBtn.addEventListener('click', () => {
        if (currentState === 'READY') {
            startRecording();
        } else if (currentState === 'LISTENING') {
            stopRecording();
        } else if (currentState === 'SPEAKING') {
            if ('speechSynthesis' in window) window.speechSynthesis.cancel();
            setState('READY');
        }
    });

    // Text Form Submit
    chatForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const messageText = userInput.value.trim();
        if (messageText && !isProcessing) {
            if ('speechSynthesis' in window) window.speechSynthesis.cancel();
            sendTextMessage(messageText);
        }
    });

    if ('speechSynthesis' in window) {
        window.speechSynthesis.onvoiceschanged = () => window.speechSynthesis.getVoices();
    }
});
