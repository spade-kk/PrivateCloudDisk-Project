import { chmodSync, copyFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const extensionRoot = path.resolve(fileURLToPath(new URL('.', import.meta.url)), '..')
const runtimeRoot = path.resolve(extensionRoot, '../..')
const generatorRoot = path.join(runtimeRoot, 'syntax-highlight', 'generator')
const buildRoot = path.join(runtimeRoot, 'syntax-highlight', 'build')
const packageJsonPath = path.join(extensionRoot, 'package.json')
const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf8'))

function run(command, args, cwd) {
  console.log(`[cloudflow-extension] $ ${command} ${args.join(' ')}`)
  execFileSync(command, args, { cwd, stdio: 'inherit' })
}

function pythonCommand() {
  return process.env.CLOUDFLOW_PYTHON || (process.platform === 'win32' ? 'python' : 'python3')
}

function generateStaticArtifacts() {
  const python = pythonCommand()
  run(python, [path.join(generatorRoot, 'build_spec.py'), '--force'], runtimeRoot)
  run(python, [path.join(generatorRoot, 'completion_builder.py'), '--force'], runtimeRoot)
  run(python, [path.join(generatorRoot, 'convert.py'), '--format', 'tmLanguage', '--force'], runtimeRoot)
  run(python, [path.join(generatorRoot, 'completion_convert.py'), '--force'], runtimeRoot)

  const generatedGrammar = path.join(buildRoot, 'cloudflow.tmLanguage.json')
  const packagedGrammar = path.join(extensionRoot, 'syntaxes', 'cloudflow.tmLanguage.json')
  copyFileSync(generatedGrammar, packagedGrammar)
  console.log(`[cloudflow-extension] synced ${path.relative(extensionRoot, packagedGrammar)}`)
}

function bundledPlatformDirectory() {
  return process.env.CLOUDFLOW_LS_PLATFORM || `${process.platform}-${process.arch}`
}

function buildLanguageServer() {
  const target = process.env.CLOUDFLOW_LS_TARGET || ''
  const customBinary = process.env.CLOUDFLOW_LS_BIN || ''
  const windowsTarget = target.includes('windows')
  const executable = (process.platform === 'win32' || windowsTarget) ? 'cloudflow-ls.exe' : 'cloudflow-ls'
  let sourceBinary = customBinary ? path.resolve(customBinary) : ''

  if (!sourceBinary) {
    const args = ['build', '--release', '--locked', '--package', 'cloudflow-ls', '--manifest-path', path.join(runtimeRoot, 'Cargo.toml')]
    if (target) args.push('--target', target)
    run('cargo', args, runtimeRoot)
    sourceBinary = target
      ? path.join(runtimeRoot, 'target', target, 'release', executable)
      : path.join(runtimeRoot, 'target', 'release', executable)
  }

  if (!existsSync(sourceBinary)) {
    throw new Error(`找不到 cloudflow-ls 构建产物：${sourceBinary}`)
  }

  const destinationDirectory = path.join(extensionRoot, 'bin', bundledPlatformDirectory())
  const destination = path.join(destinationDirectory, executable)
  mkdirSync(destinationDirectory, { recursive: true })
  copyFileSync(sourceBinary, destination)
  if (!windowsTarget && process.platform !== 'win32') chmodSync(destination, 0o755)
  console.log(`[cloudflow-extension] bundled ${path.relative(extensionRoot, destination)}`)
}

generateStaticArtifacts()
buildLanguageServer()
console.log(`[cloudflow-extension] prepared ${packageJson.name}@${packageJson.version}`)
