import os
import re

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find and replace ID variable declarations from Long to String
    replacements = [
        (r'val itemId:\s*Long', r'val itemId: String'),
        (r'var itemId:\s*Long', r'var itemId: String'),
        (r'itemId:\s*Long', r'itemId: String'),
        
        (r'val contactId:\s*Long', r'val contactId: String'),
        (r'contactId:\s*Long', r'contactId: String'),
        
        (r'val accountId:\s*Long', r'val accountId: String'),
        (r'accountId:\s*Long', r'accountId: String'),
        
        (r'val invoiceId:\s*Long', r'val invoiceId: String'),
        (r'invoiceId:\s*Long', r'invoiceId: String'),
        
        (r'val id:\s*Long', r'val id: String'),
        (r'id:\s*Long', r'id: String'),
        
        (r'Long\? = null', r'String? = null'), # This might be risky, but usually used for IDs
        (r'0L', r'""'), # Replace 0L defaults with empty string for UUIDs
        (r'MutableStateFlow<Long>', r'MutableStateFlow<String>'),
        (r'MutableStateFlow<Long\?>', r'MutableStateFlow<String?>'),
        (r'MutableStateFlow\(0L\)', r'MutableStateFlow("")'),
        (r'mutableStateOf<Long>', r'mutableStateOf<String>'),
        (r'mutableStateOf<Long\?>', r'mutableStateOf<String?>'),
        (r'mutableStateOf\(0L\)', r'mutableStateOf("")')
    ]

    new_content = content
    for pattern, repl in replacements:
        new_content = re.sub(pattern, repl, new_content)

    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Modified: {filepath}")

def main():
    base_dir = r"c:\Users\N.A\Desktop\افاق محاسب\Afac_Tow\app\src\main\java\com\example"
    for root, dirs, files in os.walk(base_dir):
        for file in files:
            if file.endswith('.kt') and file not in ['Entities.kt', 'AppDaos.kt', 'AppDatabase.kt']:
                filepath = os.path.join(root, file)
                process_file(filepath)

if __name__ == "__main__":
    main()
