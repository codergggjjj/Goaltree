const { app, BrowserWindow, dialog, session, shell } = require('electron')
const { mkdirSync } = require('node:fs')
const path = require('node:path')
const { startServer } = require('./server.cjs')

let server
let mainWindow
app.setName('Goaltree')
const userDataPath = path.join(app.getPath('appData'), 'Goaltree')
mkdirSync(userDataPath, { recursive: true })
app.setPath('userData', userDataPath)

if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore()
      mainWindow.focus()
    }
  })

  app.whenReady().then(async () => {
    app.setAppUserModelId('com.goaltree.desktop')
    try {
      server = await startServer(process.resourcesPath, app.getPath('userData'))
      const origin = `http://127.0.0.1:${server.port}`
      session.defaultSession.setPermissionRequestHandler((_contents, _permission, callback) => callback(false))
      mainWindow = new BrowserWindow({
        title: 'Goaltree',
        width: 1440,
        height: 900,
        minWidth: 740,
        minHeight: 520,
        show: false,
        autoHideMenuBar: true,
        icon: path.join(process.resourcesPath, 'app.ico'),
        webPreferences: {
          nodeIntegration: false,
          contextIsolation: true,
          sandbox: true,
        },
      })
      mainWindow.webContents.setWindowOpenHandler(({ url }) => {
        try {
          const parsed = new URL(url)
          if (['http:', 'https:'].includes(parsed.protocol) && parsed.hostname) {
            setImmediate(() => {
              shell.openExternal(url).catch(() => dialog.showErrorBox('无法打开链接', '请检查默认浏览器设置后重试。'))
            })
          }
        } catch { /* Reject malformed and unsupported links. */ }
        return { action: 'deny' }
      })
      mainWindow.webContents.on('will-navigate', (event, url) => {
        if (new URL(url).origin !== origin) event.preventDefault()
      })
      mainWindow.once('ready-to-show', () => mainWindow.show())
      await mainWindow.loadURL(server.url)
    } catch (error) {
      dialog.showErrorBox('Goaltree 无法启动', error.message)
      app.quit()
    }
  })

  app.on('window-all-closed', () => app.quit())
  app.on('before-quit', () => {
    if (server?.child && server.child.exitCode === null) server.child.kill()
  })
}
