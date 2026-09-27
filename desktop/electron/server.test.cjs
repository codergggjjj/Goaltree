const assert = require('node:assert/strict')
const { createServer } = require('node:net')
const path = require('node:path')
const test = require('node:test')
const { availablePort, serverFiles, waitForServer } = require('./server.cjs')

test('packaged resources resolve without an installed browser', () => {
  const files = serverFiles('C:\\Goaltree\\resources')
  assert.equal(files.java, path.join('C:\\Goaltree\\resources', 'runtime', 'bin', 'java.exe'))
  assert.equal(files.jar, path.join('C:\\Goaltree\\resources', 'goaltree-server.jar'))
})

test('allocates a free local port and waits for its health endpoint', async () => {
  const port = await availablePort(0)
  const server = createServer((socket) => {
    socket.once('data', () => socket.end('HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK'))
  })
  try {
    await new Promise((resolve) => server.listen(port, '127.0.0.1', resolve))
    await waitForServer({ exitCode: null, signalCode: null }, port, 1000)
  } finally {
    await new Promise((resolve) => server.close(resolve))
  }
})

test('stops waiting when the backend has exited', async () => {
  await assert.rejects(waitForServer({ exitCode: 1, signalCode: null }, 1, 100), /启动失败/)
})

test('reports a process launch error instead of crashing', async () => {
  await assert.rejects(waitForServer({ spawnError: new Error('access denied') }, 1, 100), /access denied/)
})

test('reports when the stable app port is already in use', async () => {
  const server = createServer()
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  try {
    await assert.rejects(availablePort(server.address().port), /端口已被占用/)
  } finally {
    await new Promise((resolve) => server.close(resolve))
  }
})
