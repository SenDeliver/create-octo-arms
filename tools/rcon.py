import os, socket, struct, sys, time
def pkt(i,t,b): d=struct.pack('<ii',i,t)+b.encode()+b'\x00\x00'; return struct.pack('<i',len(d))+d
def recv(s):
    n=struct.unpack('<i',s.recv(4))[0]; d=b''
    while len(d)<n: d+=s.recv(n-len(d))
    return d[8:-2].decode(errors='replace')
PROPS='run/server/server.properties'
props=dict(l.strip().split('=',1) for l in open(PROPS) if '=' in l and not l.startswith('#'))
s=socket.create_connection(('127.0.0.1',int(props['rcon.port']))); s.sendall(pkt(1,3,props['rcon.password'])); recv(s)
for line in sys.stdin:
    line=line.strip()
    if not line or line.startswith('# '): continue
    if line.startswith('#sleep'): time.sleep(float(line.split()[1])); continue
    if line.startswith('#await'):
        f = 'run/signal/c_' + line.split()[1]; t0 = time.time()
        while not os.path.exists(f) and time.time() - t0 < 180: time.sleep(0.1)
        print('# await', line.split()[1], 'ok' if os.path.exists(f) else 'TIMEOUT', time.strftime('%H:%M:%S'), flush=True); continue
    if line.startswith('#signal'):
        for d in ('run/signal', 'run2/signal'):
            os.makedirs(d, exist_ok=True); open(d + '/' + line.split()[1], 'w').close()
        print('# signal', line.split()[1], time.strftime('%H:%M:%S'), flush=True); continue
    s.sendall(pkt(2,2,line)); print(time.strftime('%H:%M:%S'), '>',line,'=>',recv(s)[:4000], flush=True)
