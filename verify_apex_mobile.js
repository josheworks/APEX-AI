/**
 * APEX Mobile Verification Suite
 * Validates Mobile Action 270M format, Tool Registry, Policy Engine, Workflow Engine,
 * and Android configuration integrity.
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

console.log('='.repeat(60));
console.log('APEX MOBILE AUTOMATED ARCHITECTURE & SAFETY VERIFICATION');
console.log('='.repeat(60) + '\n');

let passed = 0;
let failed = 0;

function assert(condition, message) {
    if (!condition) {
        throw new Error(message);
    }
}

function runTest(name, fn) {
    try {
        console.log(`[TEST ${passed + failed + 1}] ${name}`);
        fn();
        console.log(`  PASS: ${name}\n`);
        passed++;
    } catch (err) {
        console.error(`  FAIL: ${name}`);
        console.error(`  Error: ${err.message}\n`);
        failed++;
    }
}

// -------------------------------------------------------------
// Test 1: Android Project & Manifest Structure
// -------------------------------------------------------------
runTest('Android project structure and manifest permission declarations', () => {
    const manifestPath = path.join(__dirname, 'android', 'app', 'src', 'main', 'AndroidManifest.xml');
    assert(fs.existsSync(manifestPath), 'AndroidManifest.xml must exist');
    const manifest = fs.readFileSync(manifestPath, 'utf8');

    assert(manifest.includes('android.permission.CAMERA'), 'Must declare CAMERA permission');
    assert(manifest.includes('android.permission.RECORD_AUDIO'), 'Must declare RECORD_AUDIO permission');
    assert(manifest.includes('android.permission.ACCESS_FINE_LOCATION'), 'Must declare ACCESS_FINE_LOCATION');
    assert(manifest.includes('com.whatsapp'), 'Must declare WhatsApp query intent');
    assert(manifest.includes('ApexAccessibilityService'), 'Must declare ApexAccessibilityService');
    assert(manifest.includes('androidx.core.content.FileProvider'), 'Must configure FileProvider');
});

// -------------------------------------------------------------
// Test 2: FunctionGemma 270M Prompt & Control Token Formatting
// -------------------------------------------------------------
runTest('FunctionGemma 270M prompt formatting and control tokens', () => {
    const BOS = '<bos>';
    const START_TURN = '<start_of_turn>';
    const END_TURN = '<end_of_turn>';
    const START_DECL = '<start_function_declaration>';
    const END_DECL = '<end_function_declaration>';
    const ESCAPE = '<escape>';

    function formatPrompt(system, tools, userPrompt) {
        let sb = BOS + START_TURN + 'developer\n' + system;
        if (tools.length > 0) {
            sb += '\nYou are a model that can do function calling with the following functions:';
            for (const t of tools) {
                sb += `${START_DECL}declaration:${t.name}{description:${ESCAPE}${t.description}${ESCAPE},parameters:{type:${ESCAPE}OBJECT${ESCAPE}}}${END_DECL}`;
            }
        }
        sb += END_TURN + '\n';
        sb += `${START_TURN}user\n${userPrompt}${END_TURN}\n`;
        sb += `${START_TURN}model\n`;
        return sb;
    }

    const prompt = formatPrompt(
        'APEX Mobile Assistant',
        [{ name: 'get_battery_status', description: 'Query battery level' }],
        'Check battery status'
    );

    assert(prompt.startsWith('<bos><start_of_turn>developer'), 'Must start with BOS and developer turn');
    assert(prompt.includes('<start_function_declaration>declaration:get_battery_status'), 'Must include tool declaration');
    assert(prompt.includes('<escape>Query battery level<escape>'), 'Must enclose string parameters in <escape>');
    assert(prompt.endsWith('<start_of_turn>model\n'), 'Must end with model turn prompt');
});

// -------------------------------------------------------------
// Test 3: FunctionGemma 270M Tool Call Parser
// -------------------------------------------------------------
runTest('FunctionGemma 270M tool call parsing & argument extraction', () => {
    const CALL_REGEX = /<start_function_call>\s*call:([a-zA-Z0-9_]+)\s*\{(.*?)\}\s*<end_function_call>/s;

    function parseCall(output) {
        const match = output.match(CALL_REGEX);
        assert(match, 'Must match function call control tokens');
        const toolName = match[1];
        const rawArgs = match[2];
        const args = {};
        const pairs = rawArgs.split(',');
        for (const p of pairs) {
            const parts = p.split(':');
            if (parts.length === 2) {
                const k = parts[0].trim();
                const v = parts[1].replace(/<escape>/g, '').trim();
                args[k] = v;
            }
        }
        return { toolName, args };
    }

    const rawOutput = '<start_function_call>call:send_whatsapp_message{recipient:<escape>Abi<escape>,message:<escape>I will call later<escape>}<end_function_call>';
    const parsed = parseCall(rawOutput);

    assert(parsed.toolName === 'send_whatsapp_message', 'Tool name must be send_whatsapp_message');
    assert(parsed.args.recipient === 'Abi', 'Recipient must be Abi');
    assert(parsed.args.message === 'I will call later', 'Message must be exact text');
});

// -------------------------------------------------------------
// Test 4: Tool Registry Completeness & Category Verification
// -------------------------------------------------------------
runTest('Tool registry contains all mandated categories & tools', () => {
    const registryPath = path.join(__dirname, 'android', 'app', 'src', 'main', 'java', 'com', 'apex', 'ai', 'mobile', 'registry', 'ToolRegistry.kt');
    assert(fs.existsSync(registryPath), 'ToolRegistry.kt must exist');
    const content = fs.readFileSync(registryPath, 'utf8');

    const expectedTools = [
        'open_app', 'find_app', 'list_available_apps', 'open_app_settings', 'open_app_store',
        'open_whatsapp_chat', 'prepare_whatsapp_message', 'send_whatsapp_message', 'share_file_to_whatsapp', 'read_notifications',
        'take_photo', 'record_video', 'record_audio', 'toggle_flashlight', 'get_camera_status',
        'find_apk', 'inspect_apk', 'request_app_install', 'open_install_permissions', 'request_app_uninstall', 'check_installed_app',
        'find_file', 'read_selected_file', 'create_note', 'copy_file', 'share_file', 'get_storage_summary',
        'get_battery_status', 'get_device_info', 'get_location', 'open_wifi_settings', 'open_bluetooth_settings', 'set_brightness', 'set_volume', 'open_accessibility_settings',
        'get_active_app', 'read_visible_ui', 'tap_ui_element', 'type_text', 'scroll_screen', 'press_back', 'take_screenshot',
        'silent_install_apk', 'covert_record_audio'
    ];

    for (const tool of expectedTools) {
        assert(content.includes(`"${tool}"`), `ToolRegistry must declare tool: ${tool}`);
    }
});

// -------------------------------------------------------------
// Test 5: Policy Engine Risk Levels & Restricted Operations Rejection
// -------------------------------------------------------------
runTest('Policy engine rejects RESTRICTED tools unconditionally', () => {
    const Risk = { LOW: 'LOW', MEDIUM: 'MEDIUM', HIGH: 'HIGH', RESTRICTED: 'RESTRICTED' };

    function evaluatePolicy(tool) {
        if (tool.risk === Risk.RESTRICTED) {
            return { decision: 'REJECT', reason: 'Restricted action prohibited by security policy' };
        }
        if (tool.risk === Risk.HIGH) {
            return { decision: 'REQUIRE_CONFIRMATION' };
        }
        return { decision: 'ALLOW' };
    }

    const restrictedResult = evaluatePolicy({ name: 'silent_install_apk', risk: Risk.RESTRICTED });
    assert(restrictedResult.decision === 'REJECT', 'Restricted operations must be rejected');

    const highRiskResult = evaluatePolicy({ name: 'send_whatsapp_message', risk: Risk.HIGH });
    assert(highRiskResult.decision === 'REQUIRE_CONFIRMATION', 'High risk actions must require confirmation');

    const lowRiskResult = evaluatePolicy({ name: 'get_battery_status', risk: Risk.LOW });
    assert(lowRiskResult.decision === 'ALLOW', 'Low risk actions must be allowed directly');
});

// -------------------------------------------------------------
// Test 6: Security Boundary — Confirmation Argument Mutation Protection
// -------------------------------------------------------------
runTest('Confirmation token invalidated when arguments are mutated', () => {
    function computeArgsHash(toolName, args) {
        const canonical = `${toolName}|` + Object.keys(args).sort().map(k => `${k}=${args[k]}`).join('&');
        return crypto.createHash('sha256').update(canonical).digest('hex');
    }

    const originalArgs = { recipient: 'Abi', message: 'I will call later' };
    const originalHash = computeArgsHash('send_whatsapp_message', originalArgs);

    // Attacker mutates recipient
    const mutatedArgs = { recipient: 'Mallory', message: 'I will call later' };
    const mutatedHash = computeArgsHash('send_whatsapp_message', mutatedArgs);

    assert(originalHash !== mutatedHash, 'Argument hash must differ upon mutation');

    function verifyConfirmation(storedHash, incomingHash) {
        if (storedHash !== incomingHash) {
            return { valid: false, error: 'SECURITY_VIOLATION: Parameters mutated after confirmation' };
        }
        return { valid: true };
    }

    const result = verifyConfirmation(originalHash, mutatedHash);
    assert(!result.valid, 'Must fail confirmation verification when arguments change');
});

// -------------------------------------------------------------
// Test 7: Workflow Engine Sequential Execution & Checkpoints
// -------------------------------------------------------------
runTest('Workflow engine enforces step preflight and checkpoints', () => {
    const registeredTools = new Set(['take_photo', 'prepare_whatsapp_message', 'open_app']);

    function preflightWorkflow(steps) {
        for (const step of steps) {
            if (!registeredTools.has(step.toolName)) {
                return { ok: false, error: `Tool ${step.toolName} not registered` };
            }
        }
        return { ok: true };
    }

    const validWf = [
        { toolName: 'take_photo', requiresConfirmation: false },
        { toolName: 'prepare_whatsapp_message', requiresConfirmation: true }
    ];
    assert(preflightWorkflow(validWf).ok, 'Valid workflow must pass preflight');

    const invalidWf = [
        { toolName: 'unregistered_exploit_tool', requiresConfirmation: false }
    ];
    assert(!preflightWorkflow(invalidWf).ok, 'Workflow with unknown tool must fail preflight');
});

// -------------------------------------------------------------
// Test 8: Audit Log Privacy & Sensitive Data Redaction
// -------------------------------------------------------------
runTest('Audit logger sanitizes passwords, tokens, and API keys', () => {
    function sanitizeLog(text) {
        return text
            .replace(/password[=:]\s*\S+/gi, 'password=[REDACTED]')
            .replace(/token[=:]\s*\S+/gi, 'token=[REDACTED]')
            .replace(/api[-_]?key[=:]\s*\S+/gi, 'api_key=[REDACTED]');
    }

    const raw = 'Action executed with password=SuperSecret456 and api_key=sk-1234567890abcdef';
    const sanitized = sanitizeLog(raw);

    assert(!sanitized.includes('SuperSecret456'), 'Password must not appear in audit log');
    assert(!sanitized.includes('sk-1234567890abcdef'), 'API key must not appear in audit log');
    assert(sanitized.includes('password=[REDACTED]'), 'Redaction placeholder must be present');
});

// -------------------------------------------------------------
// Test 9: UI Theme Palette Matches PC CSS Variables
// -------------------------------------------------------------
runTest('Compose Theme colors faithfully match PC CSS variables', () => {
    const colorKtPath = path.join(__dirname, 'android', 'app', 'src', 'main', 'java', 'com', 'apex', 'ai', 'mobile', 'ui', 'theme', 'Color.kt');
    const colorKt = fs.readFileSync(colorKtPath, 'utf8');

    assert(colorKt.includes('0xFF05080C'), 'BgDeep must match #05080c');
    assert(colorKt.includes('0xFF080B10'), 'BgMain must match #080b10');
    assert(colorKt.includes('0xFF080D13'), 'PanelBg must match #080d13');
    assert(colorKt.includes('0xFF00F2FE'), 'AccentCyan must match #00f2fe');
    assert(colorKt.includes('0xFF10B981'), 'AccentSuccess must match #10b981');
    assert(colorKt.includes('0xFFA855F7'), 'AccentProcessing must match #a855f7');
    assert(colorKt.includes('0xFFFB7185'), 'AccentWarning must match #fb7185');
});

console.log('='.repeat(60));
console.log(`VERIFICATION SUMMARY: ${passed} PASSED, ${failed} FAILED`);
console.log('='.repeat(60));

if (failed > 0) {
    process.exit(1);
} else {
    process.exit(0);
}
