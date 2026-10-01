"""Test-only localhost TCP relay; adds per-direction delay without inspecting packets."""
import asyncio,sys
async def connection(reader,writer):
 try:
  upstream,output=await asyncio.open_connection('127.0.0.1',int(sys.argv[2]))
  async def pipe(source,dest):
   try:
    while data:=await source.read(65536):
     await asyncio.sleep(float(sys.argv[3])/1000);dest.write(data);await dest.drain()
   finally:dest.close()
  await asyncio.gather(pipe(reader,output),pipe(upstream,writer))
 except (ConnectionError,asyncio.IncompleteReadError):writer.close()
async def main():
 server=await asyncio.start_server(connection,'127.0.0.1',int(sys.argv[1]))
 async with server:await server.serve_forever()
asyncio.run(main())
