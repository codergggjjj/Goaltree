const { spawn } = require('node:child_process')
const { createServer } = require('node:net')
const { existsSync, mkdirSync, openSync, closeSync } = require('node:fs')
const path = require('node:path')

function serverFiles(resourcesPath) {
  return {
    java: path.join(resourcesPath, 'runtime', 'bin', 'java.exe'),
    jar: path.join(resourcesPath, 'goaltree-server.jar'),
  }
}

function availablePort(port = 18753) {
  return new Promise((resolve, reject) => {
    const server = createServer()
    server.once('error', (error) => reject(error.code === 'EADDRINUSE'
      ? new Error('Goaltree 使用的本地端口已被占用，请先关闭之前打开的应用。')
      : error))
    server.listen(port, '127.0.0.1', () => {
      const address = server.address()
      server.close((error) => error ? reject(error) : resolve(address.port))
    })
  })
}

async function waitForServer(child, port, timeoutMs = 40000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (child.spawnError) {
      throw new Error(`本地服务无法启动：${child.spawnError.message}`)
    }
    if (child.exitCode !== null || child.signalCode !== null) {
      throw new Error('本地服务启动失败。请检查应用日志和本机 MySQL。')
    }
    try {
      const response = await fetch(`http://127.0.0.1:${port}/api/system/health`, {
        signal: AbortSignal.timeout(800),
      })
      if (response.ok) return
    } catch {
      // The server may still be starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error('本地服务启动超时。请检查应用日志和本机 MySQL。')
}

async function startServer(resourcesPath, userDataPath) {
  const { java, jar } = serverFiles(resourcesPath)
  if (!existsSync(java) || !existsSync(jar)) {
    throw new Error('应用文件不完整，请重新解压整个 Goaltree 文件夹。')
  }

  // A stable origin keeps each tree's local canvas position across app restarts.
  const port = await availablePort()
  mkdirSync(userDataPath, { recursive: true })
  const logPath = path.join(userDataPath, 'server.log')
  const log = openSync(logPath, 'a')
  let child
  try {
    child = spawn(java, [
      '-jar', jar,
      '--server.address=127.0.0.1',
      `--server.port=${port}`,
      `--app.frontend-url=http://127.0.0.1:${port}`,
    ], {
      windowsHide: true,
      stdio: ['ignore', log, log],
    })
    child.on('error', (error) => { child.spawnError = error })
  } finally {
    closeSync(log)
  }

  try {
    await waitForServer(child, port)
  } catch (error) {
    child.kill()
    throw new Error(`${error.message}\n日志：${logPath}`)
  }
  return { child, port, url: `http://127.0.0.1:${port}/goals` }
}

module.exports = { availablePort, serverFiles, startServer, waitForServer }
