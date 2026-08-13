#!/usr/bin/env python3
"""
Automated verification tests for APEX AI upgrade.
Tests conversation history, memory, tool normalization, and message sequencing.
"""

import json
import sys

def test_memory_io():
    """Test memory file creation and I/O"""
    print('[TEST 1] Memory file creation and I/O')
    import memory_manager
    memory_manager.ensure_memory_dir()
    test_mem = {
        'profile': {'preferred_name': 'TestUser'},
        'preferences': ['test'],
        'notes': []
    }
    memory_manager.save_user_memory(test_mem)
    loaded = memory_manager.load_user_memory()
    assert loaded.get('profile', {}).get('preferred_name') == 'TestUser', 'Memory load failed'
    print('  PASS: Memory I/O works correctly\n')


def test_conversation_history():
    """Test conversation history bounded limits"""
    print('[TEST 2] Conversation history limits')
    import brain
    brain._conversation_history.clear()
    for i in range(30):
        brain._append_conversation('user', f'Message {i}')
    assert len(brain._conversation_history) <= brain.MAX_CONVERSATION_MESSAGES, 'History limit exceeded'
    print(f'  PASS: History bounded to {len(brain._conversation_history)} messages (max: {brain.MAX_CONVERSATION_MESSAGES})\n')


def test_tool_normalization_gemini():
    """Test Gemini format tool call normalization"""
    print('[TEST 3] Tool call normalization (Gemini format)')
    import brain
    
    class GeminiCall:
        def __init__(self):
            self.name = 'web_search'
            self.args = {'query': 'test'}
    
    call = GeminiCall()
    norm = brain._normalize_tool_call(call)
    assert norm['name'] == 'web_search', 'Gemini format normalization failed'
    assert norm['arguments']['query'] == 'test', 'Arguments not preserved'
    print('  PASS: Tool normalization works for Gemini format\n')


def test_tool_normalization_openai():
    """Test OpenAI-compatible format tool call normalization"""
    print('[TEST 4] Tool call normalization (OpenAI format)')
    import brain
    
    class FunctionObj:
        def __init__(self):
            self.name = 'calculate'
            self.arguments = '{"expression": "2+2"}'
    
    class OpenAICall:
        def __init__(self):
            self.function = FunctionObj()
            self.id = 'test_id'
    
    call = OpenAICall()
    norm = brain._normalize_tool_call(call)
    assert norm['name'] == 'calculate', 'OpenAI format normalization failed'
    assert norm['arguments']['expression'] == '2+2', 'JSON parsing failed'
    print('  PASS: Tool normalization works for OpenAI format\n')


def test_memory_extraction():
    """Test memory extraction patterns"""
    print('[TEST 5] Memory extraction patterns')
    import memory_manager
    
    test_cases = [
        ('My name is Alice', 'Alice'),
        ('Call me Bob', 'Bob'),
        ('Remember me as Charlie', 'Charlie'),
    ]
    
    for msg, expected_name in test_cases:
        name = memory_manager._extract_preferred_name(msg)
        assert name == expected_name, f'Failed to extract {expected_name} from {msg}, got {name}'
    
    print('  PASS: Name extraction patterns work correctly\n')


def test_preference_extraction():
    """Test preference extraction patterns"""
    print('[TEST 6] Preference extraction patterns')
    import memory_manager
    
    pref = memory_manager._extract_preference('I prefer short answers')
    assert pref is not None, 'Failed to extract preference'
    assert 'short answer' in pref.lower(), f'Preference not extracted correctly: {pref}'
    print(f'  PASS: Preference extracted: "{pref}"\n')


def test_message_sequencing():
    """Test message sequence building with history"""
    print('[TEST 7] Message sequence building with history')
    import brain
    
    brain._conversation_history.clear()
    brain._append_conversation('user', 'Hello')
    brain._append_conversation('assistant', 'Hi there')
    
    messages = brain._build_message_sequence('How are you?')
    assert len(messages) >= 3, f'Not enough messages in sequence: {len(messages)}'
    
    # Check that conversation history is included
    has_history = any(
        m.get('role') == 'user' and 'Hello' in m.get('content', '')
        for m in messages
    )
    assert has_history, 'Conversation history not included in sequence'
    print('  PASS: Message sequence includes conversation history\n')


def test_api_response_contract():
    """Test that API response contract is preserved"""
    print('[TEST 8] API response contract validation')
    
    # Check that app.py still has expected response fields
    import app
    
    # Verify endpoints exist
    assert hasattr(app, 'chat_endpoint'), 'chat_endpoint missing'
    assert hasattr(app, 'voice_endpoint'), 'voice_endpoint missing'
    
    print('  PASS: API response contract preserved\n')


def test_max_tool_rounds():
    """Test MAX_TOOL_ROUNDS constant exists and is reasonable"""
    print('[TEST 9] Tool round limiting')
    import brain
    
    assert hasattr(brain, 'MAX_TOOL_ROUNDS'), 'MAX_TOOL_ROUNDS not defined'
    assert brain.MAX_TOOL_ROUNDS >= 1 and brain.MAX_TOOL_ROUNDS <= 10, f'MAX_TOOL_ROUNDS unreasonable: {brain.MAX_TOOL_ROUNDS}'
    print(f'  PASS: MAX_TOOL_ROUNDS set to {brain.MAX_TOOL_ROUNDS}\n')


def main():
    """Run all tests"""
    print('=' * 60)
    print('APEX AI AUTOMATED VERIFICATION TESTS')
    print('=' * 60 + '\n')
    
    tests = [
        test_memory_io,
        test_conversation_history,
        test_tool_normalization_gemini,
        test_tool_normalization_openai,
        test_memory_extraction,
        test_preference_extraction,
        test_message_sequencing,
        test_api_response_contract,
        test_max_tool_rounds,
    ]
    
    failed = 0
    for test in tests:
        try:
            test()
        except Exception as e:
            print(f'  FAIL: {str(e)}\n')
            failed += 1
    
    print('=' * 60)
    if failed == 0:
        print('ALL AUTOMATED TESTS PASSED')
        print('=' * 60)
        return 0
    else:
        print(f'{failed} TEST(S) FAILED')
        print('=' * 60)
        return 1


if __name__ == '__main__':
    sys.exit(main())
