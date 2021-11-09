#!/usr/bin/env python3

import asyncio
import websockets
import struct
import pyvjoy
import sys
import traceback


event_codes = '{"ANALOG_LX":0,"ANALOG_LY":1,"ANALOG_RX":2,"ANALOG_RY":3,"L1":4,"L2":5,"R1":6,"R2":7,"TRIANGLE":8,"CROSS":9,"SQUARE":10,"CIRCLE":11,"UP":12,"DOWN":13,"LEFT":14,"RIGHT":15,"SELECT":16,"START":17}'
vjoy = pyvjoy.VJoyDevice(1)
log = lambda data: print(data) if "-v" in sys.argv else 1

async def echo(websocket, _):
    print("Connection recieved")
    await websocket.send(event_codes.encode())
    async for message in websocket:
        packet = struct.unpack('<'+'h'*(len(message)//2), message)
        for i in range(0,len(packet), 2):
            code = packet[i]
            value = packet[i+1]
            log((code, value))
            send_command(code, value)


def send_command(code, value):
    if code == 0:
        vjoy.set_axis(pyvjoy.HID_USAGE_X, value//2 + 16384)
    elif code == 1:
        vjoy.set_axis(pyvjoy.HID_USAGE_Y, value//2 + 16384)
    elif code == 2:
        vjoy.set_axis(pyvjoy.HID_USAGE_RX, value//2 + 16384)
    elif code == 3:
        vjoy.set_axis(pyvjoy.HID_USAGE_RY, value//2 + 16384)
    else:
        code -= 1
        vjoy.set_button(code, 1 if value else 0 )


async def main():
    try:
        import socket
        ip = socket.gethostbyname(socket.gethostname())
        print(f"Server started on {ip}:8765")
        async with websockets.serve(echo, ip, 8765):
            await asyncio.Future()  # run forever
    except Exception as e:
        traceback.print_exc()

asyncio.run(main())