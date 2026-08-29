#!/usr/bin/env node
// ============================================================
// index.js — IM 测试客户端主入口
// ============================================================
// 命令行用法:
//   node src/index.js [options]
//
// 选项:
//   --host <host>           服务器地址 (默认 localhost)
//   --port <port>           服务器端口 (默认 9090)
//   --token <token>         JWT 认证 Token
//   --user <userId>         当前用户 ID
//   --to <receiverId>       默认接收方 ID
//   --conv <conversationId> 默认会话 ID
//   --heartbeat <ms>        心跳间隔 (默认 30000)
//   --config <path>         配置文件路径
//   --verbose               详细调试模式
//   --send <content>        非交互模式：发送单条消息后退出
//   --help                  显示帮助
// ============================================================

import { program } from 'commander'
import readline from 'node:readline'
import { buildConfig, buildWsUrl } from './config.js'
import { Logger } from './logger.js'
import { IMConnection, ConnectionState } from './connection.js'
import { CommandHandler } from './commands.js'
import chalk from 'chalk'

// ==================== 命令行解析 ====================

program
  .name('im-test')
  .description('PrivateCloudDisk IM v2 二进制协议测试客户端')
  .option('--host <host>', '服务器地址', 'localhost')
  .option('--port <port>', '服务器端口', '9090')
  .option('--token <token>', 'JWT 认证 Token')
  .option('--user <userId>', '当前用户 ID')
  .option('--to <receiverId>', '默认接收方 ID')
  .option('--conv <conversationId>', '默认会话 ID')
  .option('--heartbeat <ms>', '心跳间隔（毫秒）', '30000')
  .option('--config <path>', '配置文件路径')
  .option('--verbose', '详细调试模式')
  .option('--send <content>', '非交互模式：发送单条消息后退出')
  .helpOption('-h, --help', '显示帮助')
  .parse()

const options = program.opts()

// ==================== 初始化 ====================

const config = buildConfig(options)
const logger = new Logger(config.logging)
const connection = new IMConnection(config, logger)
const cmdHandler = new CommandHandler(connection, logger, config)

// ==================== 事件绑定 ====================

// 连接状态变更
connection.addEventListener('state', (e) => {
  const { state } = e.detail
  if (state === ConnectionState.AUTHENTICATED) {
    logger.success('✓ 连接已就绪，可以开始发送消息')
    // 仿照 box-im 客户端设计：连接就绪后自动通过 HTTP 拉取离线消息
    cmdHandler.pullOffline().catch((err) => {
      logger.warn(`自动拉取离线消息失败: ${err.message}`)
    })
    if (options.send) {
      // 非交互模式
      const args = options.send.split(' ')
      cmdHandler.cmdSend(args)
      setTimeout(() => {
        connection.disconnect('non-interactive mode')
        process.exit(0)
      }, 3000)
    }
  }
})

// 收到加密消息
connection.addEventListener('envelope', (e) => {
  cmdHandler.handleReceivedEnvelope(e.detail.envelope)
})

// 错误事件
connection.addEventListener('error', (e) => {
  logger.error(`连接错误: ${e.detail.error?.message || 'unknown'}`)
})

// 连接关闭
connection.addEventListener('close', (e) => {
  logger.warn(`连接已关闭: code=${e.detail.code}, reason=${e.detail.reason}`)
})

// 心跳
connection.addEventListener('heartbeat', (e) => {
  // 已在 logger.heartbeat 中输出
})

// ==================== 帮助信息 ====================

function showBanner() {
  console.log(chalk.cyan(`
╔══════════════════════════════════════════════════╗
║     PrivateCloudDisk IM v2 Test Client          ║
║     二进制协议调试工具 (Protobuf + AES-256-GCM)  ║
╚══════════════════════════════════════════════════╝
  `))
  console.log(chalk.gray(`  服务器: ${config.server.host}:${config.server.port}${config.server.path}`))
  console.log(chalk.gray(`  用户:   ${config.user.userId || '(未设置)'}`))
  console.log(chalk.gray(`  接收方: ${config.user.receiverId || '(未设置)'}`))
  console.log(chalk.gray(`  心跳:   ${config.heartbeat.intervalMs}ms`))
  console.log(chalk.gray(`  调试:   ${config.logging.verbose ? 'ON' : 'OFF'}`))
  console.log('')
}

function showInteractiveHelp() {
  const commands = cmdHandler.getCommands()
  console.log(chalk.cyan('可用命令:'))
  for (const [name, cmd] of commands) {
    console.log(`  ${chalk.green(name.padEnd(20))} ${chalk.gray(cmd.description)}`)
  }
  console.log('')
  console.log(chalk.gray('提示: 直接输入文本即发送消息给当前接收方'))
  console.log('')
}

// ==================== 交互式 REPL ====================

function startRepl() {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    prompt: chalk.cyan('im> '),
    terminal: true,
  })

  console.log(chalk.green('输入 help 查看可用命令，或直接输入消息内容发送\n'))
  rl.prompt()

  rl.on('line', (line) => {
    const input = line.trim()
    if (!input) {
      rl.prompt()
      return
    }

    // 解析命令
    const parts = input.split(/\s+/)
    const cmdName = parts[0]
    const args = parts.slice(1)

    const commands = cmdHandler.getCommands()

    if (commands.has(cmdName)) {
      // 执行命令
      try {
        commands.get(cmdName).handler(args)
      } catch (e) {
        logger.error(`命令执行失败: ${e.message}`)
      }
    } else {
      // 非命令 — 作为消息发送
      cmdHandler.cmdSend([input])
    }

    rl.prompt()
  })

  rl.on('close', () => {
    console.log(chalk.gray('\n正在退出...'))
    connection.disconnect('user exit')
    setTimeout(() => process.exit(0), 500)
  })

  // Ctrl+C 处理
  process.on('SIGINT', () => {
    rl.close()
  })
}

// ==================== 启动 ====================

async function main() {
  showBanner()

  // 验证必要参数
  if (!config.auth.token) {
    logger.error('缺少认证 Token，请通过 --token 或配置文件提供')
    process.exit(1)
  }
  if (!config.user.userId) {
    logger.error('缺少用户 ID，请通过 --user 或配置文件提供')
    process.exit(1)
  }

  // 构建连接 URL
  const url = buildWsUrl(config)
  logger.debug(`连接 URL: ${url.replace(/\?token=.*/, '?token=***')}`)

  // 建立连接
  connection.connect(url)

  // 启动交互式命令行（非交互模式下等待认证后自动发送）
  if (!options.send) {
    startRepl()
  }
}

main().catch((err) => {
  logger.error(`启动失败: ${err.message}`)
  process.exit(1)
})
