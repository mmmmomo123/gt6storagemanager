import zlib, struct

# 16x16 占位机器贴图：金属灰底 + 深色描边 + 中间两条"抽屉"横条 + 一个小把手
def img():
    px = [[(0,0,0,255)]*16 for _ in range(16)]
    def col(x,y,c):
        if 0 <= x < 16 and 0 <= y < 16: px[y][x]=c
    base=(158,160,164,255); edge=(74,76,80,255); inset=(120,122,126,255)
    slat=(96,98,102,255); hi=(190,192,196,255); accent=(232,178,60,255)
    for y in range(16):
        for x in range(16):
            col(x,y,base)
    for i in range(16):                       # 描边
        col(i,0,edge); col(i,15,edge); col(0,i,edge); col(15,i,edge)
    for y in range(2,14):                     # 内凹面板
        for x in range(2,14): col(x,y,inset)
    for x in range(2,14):                     # 顶部高光
        col(x,2,hi)
    for x in range(3,13):                     # 抽屉横条 1
        col(x,6,slat); col(x,7,slat)
    for x in range(3,13):                     # 抽屉横条 2
        col(x,10,slat); col(x,11,slat)
    for x in range(4,12):                     # 条间分隔高光
        col(x,5,hi); col(x,9,hi)
    for y in range(6,8):                      # 中央把手（金色）
        for x in range(7,9): col(x,y,accent)
    for y in range(10,12):
        for x in range(7,9): col(x,y,accent)
    return px

def png(path, px):
    w=h=16
    raw=b''.join(b'\x00'+bytes(v for p in row for v in p) for row in px)
    def chunk(t,d):
        c=t+d
        return struct.pack('>I',len(d))+c+struct.pack('>I',zlib.crc32(c)&0xffffffff)
    with open(path,'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0)))
        f.write(chunk(b'IDAT',zlib.compress(raw,9)))
        f.write(chunk(b'IEND',b''))

png('src/main/resources/assets/gtsm/textures/blocks/machine_storage_manager.png', img())
print('texture written')
