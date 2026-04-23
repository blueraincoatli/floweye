import base64

c = base64.b64decode(
b'IyEvdXNyL2Jpbi9lbnYgcHl0aG9uMwo=' +
b'Cg=='
).decode('utf-8')

with open('scanning_coordinator.py', 'w', encoding='utf-8') as f:
    f.write(c)
print('done')
