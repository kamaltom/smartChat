(function() {
    'use strict';

    class ChatbotWidget {
        constructor(config = {}) {
            this.config = {
                apiUrl: config.apiUrl || '/api/chat/ask',
                theme: config.theme || 'light',
                position: config.position || 'bottom-right',
                title: config.title || 'Smart Chat',
                placeholder: config.placeholder || 'Type your question...',
                autoOpen: config.autoOpen || false,
                initialMessage: config.initialMessage || "Hello! I'm your smart chat assistant. How can I help you today?",
                calendlyUrl: config.calendlyUrl || null,
                ...config
            };
            this.isOpen = false;
            this.messages = [];
            this.conversationId = this.generateConversationId();
            this.conversationState = null; // Track conversation state
            this.collectedDetails = {}; // Store collected details
            this.init();
        }

        init() {
            this.createStyles();
            this.createWidget();
            this.attachEventListeners();
        }

        createStyles() {
            if (document.getElementById('chatbot-widget-styles')) return;

            const styles = `
                .chatbot-widget {
                    position: fixed;
                    z-index: 10000;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                }

                .chatbot-widget.bottom-right {
                    bottom: 20px;
                    right: 20px;
                }

                .chatbot-widget.bottom-left {
                    bottom: 20px;
                    left: 20px;
                }

                .chatbot-toggle {
                    width: 60px;
                    height: 60px;
                    border-radius: 50%;
                    background: #007bff;
                    border: none;
                    cursor: pointer;
                    box-shadow: 0 4px 12px rgba(0, 123, 255, 0.3);
                    transition: all 0.3s ease;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: white;
                    font-size: 24px;
                }

                .chatbot-toggle:hover {
                    transform: scale(1.1);
                    box-shadow: 0 6px 16px rgba(0, 123, 255, 0.4);
                }

                .chatbot-panel {
                    position: absolute;
                    bottom: 80px;
                    right: 0;
                    width: 350px;
                    height: 500px;
                    background: white;
                    border-radius: 12px;
                    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
                    display: none;
                    flex-direction: column;
                    overflow: hidden;
                    border: 1px solid #e1e5e9;
                }

                .chatbot-panel.open {
                    display: flex;
                }

                .chatbot-header {
                    background: #007bff;
                    color: white;
                    padding: 16px;
                    font-weight: 600;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                }

                .chatbot-close {
                    background: none;
                    border: none;
                    color: white;
                    font-size: 20px;
                    cursor: pointer;
                    padding: 0;
                    width: 24px;
                    height: 24px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }

                .chatbot-messages {
                    flex: 1;
                    padding: 16px;
                    overflow-y: auto;
                    background: #f8f9fa;
                }

                .chatbot-message {
                    margin-bottom: 12px;
                    max-width: 80%;
                }

                .chatbot-message.user {
                    margin-left: auto;
                }

                .chatbot-message.user .message-bubble {
                    background: #007bff;
                    color: white;
                    margin-left: auto;
                }

                .chatbot-message.bot .message-bubble {
                    background: white;
                    color: #333;
                    border: 1px solid #e1e5e9;
                }

                .message-bubble {
                    padding: 12px 16px;
                    border-radius: 18px;
                    font-size: 14px;
                    line-height: 1.4;
                    word-wrap: break-word;
                }

                .chatbot-input-area {
                    padding: 16px;
                    border-top: 1px solid #e1e5e9;
                    background: white;
                }

                .chatbot-input-container {
                    display: flex;
                    gap: 8px;
                }

                .chatbot-input {
                    flex: 1;
                    padding: 12px 16px;
                    border: 1px solid #e1e5e9;
                    border-radius: 24px;
                    outline: none;
                    font-size: 14px;
                }

                .chatbot-input:focus {
                    border-color: #007bff;
                }

                .chatbot-send {
                    width: 40px;
                    height: 40px;
                    border-radius: 50%;
                    background: #007bff;
                    border: none;
                    color: white;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    transition: background 0.2s ease;
                }

                .chatbot-send:hover {
                    background: #0056b3;
                }

                .chatbot-send:disabled {
                    background: #ccc;
                    cursor: not-allowed;
                }

                .chatbot-loading {
                    display: flex;
                    align-items: center;
                    gap: 4px;
                    padding: 12px 16px;
                }

                .chatbot-loading-dot {
                    width: 8px;
                    height: 8px;
                    border-radius: 50%;
                    background: #007bff;
                    animation: chatbot-loading 1.4s infinite ease-in-out;
                }

                .chatbot-loading-dot:nth-child(1) { animation-delay: -0.32s; }
                .chatbot-loading-dot:nth-child(2) { animation-delay: -0.16s; }

                @keyframes chatbot-loading {
                    0%, 80%, 100% { 
                        transform: scale(0);
                        opacity: 0.5;
                    }
                    40% { 
                        transform: scale(1);
                        opacity: 1;
                    }
                }

                .calendly-widget {
                    background: #f8f9fa !important;
                    border: 1px solid #e1e5e9 !important;
                    padding: 16px !important;
                    max-width: 100% !important;
                }

                .calendly-widget .calendly-inline-widget {
                    border-radius: 8px;
                    overflow: hidden;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
                }

                .quick-option {
                    background: #007bff;
                    color: white;
                    border: none;
                    padding: 8px 12px;
                    border-radius: 16px;
                    font-size: 12px;
                    cursor: pointer;
                    transition: background 0.2s ease;
                }

                .quick-option:hover {
                    background: #0056b3;
                }

                .main-menu-btn {
                    background: #f8f9fa;
                    color: #333;
                    border: 1px solid #dee2e6;
                    padding: 12px 8px;
                    border-radius: 8px;
                    font-size: 13px;
                    cursor: pointer;
                    transition: all 0.2s ease;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    gap: 5px;
                }

                .main-menu-btn:hover {
                    background: #e9ecef;
                    transform: translateY(-1px);
                }

                .emergency-btn:hover {
                    background: #dc3545;
                    color: white;
                }

                .estimate-btn:hover {
                    background: #28a745;
                    color: white;
                }

                .schedule-btn:hover {
                    background: #007bff;
                    color: white;
                }

                .question-btn:hover {
                    background: #17a2b8;
                    color: white;
                }

                .technician-btn:hover {
                    background: #fd7e14;
                    color: white;
                }


                @media (max-width: 480px) {
                    .chatbot-panel {
                        width: calc(100vw - 40px);
                        height: calc(100vh - 100px);
                        bottom: 80px;
                        right: 20px;
                    }
                }
            `;

            const styleSheet = document.createElement('style');
            styleSheet.id = 'chatbot-widget-styles';
            styleSheet.textContent = styles;
            document.head.appendChild(styleSheet);
        }

        createWidget() {
            this.widget = document.createElement('div');
            this.widget.className = `chatbot-widget ${this.config.position}`;
            
            this.widget.innerHTML = `
                <button class="chatbot-toggle" aria-label="Open chat">
                    💬
                </button>
                <div class="chatbot-panel">
                    <div class="chatbot-header">
                        <span>${this.config.title}</span>
                        <button class="chatbot-close" aria-label="Close chat">×</button>
                    </div>
                    <div class="chatbot-messages"></div>
                    <div class="chatbot-input-area">
                        <div class="chatbot-input-container">
                            <input type="text" class="chatbot-input" placeholder="${this.config.placeholder}" />
                            <button class="chatbot-send" aria-label="Send message">→</button>
                        </div>
                    </div>
                </div>
            `;

            document.body.appendChild(this.widget);
            
            this.elements = {
                toggle: this.widget.querySelector('.chatbot-toggle'),
                panel: this.widget.querySelector('.chatbot-panel'),
                close: this.widget.querySelector('.chatbot-close'),
                messages: this.widget.querySelector('.chatbot-messages'),
                input: this.widget.querySelector('.chatbot-input'),
                send: this.widget.querySelector('.chatbot-send')
            };
        }

        attachEventListeners() {
            this.elements.toggle.addEventListener('click', () => this.togglePanel());
            this.elements.close.addEventListener('click', () => this.closePanel());
            this.elements.send.addEventListener('click', () => this.sendMessage());
            this.elements.input.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    this.sendMessage();
                }
            });
        }

        togglePanel() {
            this.isOpen = !this.isOpen;
            this.elements.panel.classList.toggle('open', this.isOpen);
            
            if (this.isOpen && this.messages.length === 0) {
                this.showMainMenu();
            }
            
            if (this.isOpen) {
                this.elements.input.focus();
            }
        }

        closePanel() {
            this.isOpen = false;
            this.elements.panel.classList.remove('open');
        }

        async sendMessage() {
            const message = this.elements.input.value.trim();
            if (!message) return;

            this.addUserMessage(message);
            this.elements.input.value = '';
            this.elements.send.disabled = true;

            this.showLoading();

            try {
                const response = await fetch(this.config.apiUrl, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({ 
                        question: message,
                        conversationId: this.conversationId,
                        conversationState: this.conversationState
                    })
                });

                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }

                const data = await response.json();
                this.hideLoading();
                
                this.addBotMessage(data.answer || 'Sorry, I couldn\'t process your request.');
                
                // Update conversation state
                if (data.conversationState) {
                    this.conversationState = data.conversationState;
                }
                
                // Show Calendly for schedule requests that have collected details
                if (data.nextStep === 'show_calendly') {
                    this.showCalendlyWidget();
                }
                
            } catch (error) {
                console.error('Chatbot error:', error);
                this.hideLoading();
                this.addBotMessage('Sorry, I\'m having trouble connecting. Please try again later.');
            } finally {
                this.elements.send.disabled = false;
            }
        }

        showMainMenu() {
            const menuEl = document.createElement('div');
            menuEl.className = 'chatbot-message bot';
            menuEl.innerHTML = `
                <div class="message-bubble">
                    <div style="margin-bottom: 15px;">
                        <strong>How can I help you today?</strong>
                    </div>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px;">
                        <button class="main-menu-btn emergency-btn" onclick="window.chatbotWidget.selectOption('EMERGENCY_BUTTON')">🚨 Emergency</button>
                        <button class="main-menu-btn estimate-btn" onclick="window.chatbotWidget.selectOption('ESTIMATE_BUTTON')">💰 Need Estimate</button>
                        <button class="main-menu-btn schedule-btn" onclick="window.chatbotWidget.selectOption('SCHEDULE_BUTTON')">📅 Schedule</button>
                        <button class="main-menu-btn question-btn" onclick="window.chatbotWidget.selectOption('QUESTION_BUTTON')">❓ Question</button>
                        <button class="main-menu-btn technician-btn" onclick="window.chatbotWidget.selectOption('TECHNICIAN_BUTTON')" style="grid-column: 1 / -1;">👨‍🔧 Chat with Technician</button>
                    </div>
                </div>
            `;
            
            this.elements.messages.appendChild(menuEl);
            this.elements.messages.scrollTop = this.elements.messages.scrollHeight;
        }

        selectOption(buttonType) {
            // Send the button selection to backend
            this.sendButtonSelection(buttonType);
        }

        async sendButtonSelection(buttonType) {
            this.elements.send.disabled = true;
            this.showLoading();

            try {
                const response = await fetch(this.config.apiUrl, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({ 
                        question: buttonType,
                        conversationId: this.conversationId,
                        conversationState: this.conversationState
                    })
                });

                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }

                const data = await response.json();
                this.hideLoading();
                
                this.addBotMessage(data.answer || 'Sorry, I couldn\'t process your request.');
                
                // Update conversation state
                if (data.conversationState) {
                    this.conversationState = data.conversationState;
                }
                
                // Show Calendly for schedule requests that have collected details
                if (data.nextStep === 'show_calendly') {
                    this.showCalendlyWidget();
                }
                
            } catch (error) {
                console.error('Chatbot error:', error);
                this.hideLoading();
                this.addBotMessage('Sorry, I\'m having trouble connecting. Please try again later.');
            } finally {
                this.elements.send.disabled = false;
            }
        }

        fillMessage(text) {
            this.elements.input.value = text;
            this.elements.input.focus();
        }

        addUserMessage(message) {
            this.messages.push({ type: 'user', content: message });
            this.renderMessage('user', message);
        }

        addBotMessage(message) {
            this.messages.push({ type: 'bot', content: message });
            this.renderMessage('bot', message);
        }

        renderMessage(type, content) {
            const messageEl = document.createElement('div');
            messageEl.className = `chatbot-message ${type}`;
            messageEl.innerHTML = `<div class="message-bubble">${this.formatMessage(content)}</div>`;
            
            this.elements.messages.appendChild(messageEl);
            this.elements.messages.scrollTop = this.elements.messages.scrollHeight;
        }

        formatMessage(content) {
            // Convert newlines to <br> tags and handle basic markdown-style formatting
            return content
                .replace(/\n/g, '<br>')
                .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
                .replace(/\*(.*?)\*/g, '<em>$1</em>')
                .replace(/📞/g, '<span style="color: #dc3545; font-weight: bold;">📞</span>')
                .replace(/🚨/g, '<span style="color: #dc3545; font-size: 1.2em;">🚨</span>')
                .replace(/⚠️/g, '<span style="color: #ffc107; font-weight: bold;">⚠️</span>');
        }

        showLoading() {
            const loadingEl = document.createElement('div');
            loadingEl.className = 'chatbot-message bot';
            loadingEl.innerHTML = `
                <div class="message-bubble">
                    <div class="chatbot-loading">
                        <div class="chatbot-loading-dot"></div>
                        <div class="chatbot-loading-dot"></div>
                        <div class="chatbot-loading-dot"></div>
                    </div>
                </div>
            `;
            loadingEl.setAttribute('data-loading', 'true');
            
            this.elements.messages.appendChild(loadingEl);
            this.elements.messages.scrollTop = this.elements.messages.scrollHeight;
        }

        hideLoading() {
            const loadingEl = this.elements.messages.querySelector('[data-loading="true"]');
            if (loadingEl) {
                loadingEl.remove();
            }
        }

        escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        generateConversationId() {
            return 'conv_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        }

        // Legacy method - now handled in backend
        detectSchedulingIntent(message) {
            return false; // Always return false since backend handles this
        }

        showCalendlyWidget() {
            if (!this.config.calendlyUrl) {
                this.addBotMessage("I'd be happy to help you schedule an appointment! Please call us at (555) 123-POWER to book your service.");
                return;
            }

            const messageEl = document.createElement('div');
            messageEl.className = 'chatbot-message bot';
            messageEl.innerHTML = `
                <div class="message-bubble calendly-widget">
                    <div style="margin-bottom: 10px;">
                        <strong>Schedule Your Appointment</strong>
                    </div>
                    <div class="calendly-inline-widget" 
                         data-url="${this.config.calendlyUrl}" 
                         style="min-width:280px;height:400px;"></div>
                </div>
            `;
            
            this.elements.messages.appendChild(messageEl);
            this.elements.messages.scrollTop = this.elements.messages.scrollHeight;

            // Load Calendly widget script if not already loaded
            if (!window.Calendly) {
                const script = document.createElement('script');
                script.src = 'https://assets.calendly.com/assets/external/widget.js';
                script.onload = () => {
                    if (window.Calendly) {
                        window.Calendly.initInlineWidget({
                            url: this.config.calendlyUrl,
                            parentElement: messageEl.querySelector('.calendly-inline-widget')
                        });
                    }
                };
                document.head.appendChild(script);
            } else {
                // Calendly already loaded, initialize widget
                window.Calendly.initInlineWidget({
                    url: this.config.calendlyUrl,
                    parentElement: messageEl.querySelector('.calendly-inline-widget')
                });
            }
        }
    }

    // Auto-initialize if script is loaded with data attributes
    document.addEventListener('DOMContentLoaded', () => {
        const script = document.querySelector('script[src*="chatbot-widget.js"]');
        if (script && script.hasAttribute('data-auto-init')) {
            const config = {};
            
            // Read configuration from data attributes
            if (script.hasAttribute('data-api-url')) {
                config.apiUrl = script.getAttribute('data-api-url');
            }
            if (script.hasAttribute('data-title')) {
                config.title = script.getAttribute('data-title');
            }
            if (script.hasAttribute('data-position')) {
                config.position = script.getAttribute('data-position');
            }
            if (script.hasAttribute('data-placeholder')) {
                config.placeholder = script.getAttribute('data-placeholder');
            }
            if (script.hasAttribute('data-auto-open')) {
                config.autoOpen = script.getAttribute('data-auto-open') === 'true';
            }
            if (script.hasAttribute('data-initial-message')) {
                config.initialMessage = script.getAttribute('data-initial-message');
            }
            if (script.hasAttribute('data-calendly-url')) {
                config.calendlyUrl = script.getAttribute('data-calendly-url');
            }
            
            window.chatbotWidget = new ChatbotWidget(config);
            
            // Auto-open if configured
            if (config.autoOpen) {
                setTimeout(() => {
                    window.chatbotWidget.togglePanel();
                }, 2000); // Open after 2 seconds
            }
        }
    });

    // Expose ChatbotWidget globally
    window.ChatbotWidget = ChatbotWidget;
})();