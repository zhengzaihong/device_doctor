/// 桌面端共享的进程/文件读取工具。
///
/// 所有外部命令都带超时与异常吞掉，保证在任何桌面环境下调用平台检测
/// 都不会抛错或永久挂起（与 Android 原生侧「任何探针失败即降级」的语义一致）。
library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';

/// 运行命令并返回标准输出（小写化前的原文）。失败/超时返回 null。
Future<String?> runCmd(
  String executable,
  List<String> arguments, {
  Duration timeout = const Duration(milliseconds: 1200),
}) async {
  try {
    final result = await Process.run(executable, arguments)
        .timeout(timeout, onTimeout: () {
      throw TimeoutException('runCmd timeout');
    });
    if (result.exitCode != 0) {
      // 部分命令（如 findstr 无匹配）以非 0 退出码表示“未命中”，
      // 仍可能有有用输出，交给调用方按 stdout 判断。
      final out = result.stdout?.toString().trim();
      return (out == null || out.isEmpty) ? null : out;
    }
    final out = result.stdout?.toString().trim();
    return (out == null || out.isEmpty) ? null : out;
  } catch (_) {
    return null;
  }
}

/// 运行命令并以「退出码是否为 0」作为探针结果。
/// 超时/无法启动返回 null（未知），否则 true/false。
Future<bool?> runCmdOk(
  String executable,
  List<String> arguments, {
  Duration timeout = const Duration(milliseconds: 1500),
}) async {
  try {
    final result = await Process.run(executable, arguments).timeout(timeout);
    return result.exitCode == 0;
  } catch (_) {
    return null;
  }
}

/// 安全读取文本文件，失败返回 null（不抛异常）。
Future<String?> readFileSafe(String path) async {
  try {
    final f = File(path);
    if (!await f.exists()) return null;
    final s = await f.readAsString();
    return s;
  } catch (_) {
    return null;
  }
}

/// 读取文件并按行返回，失败返回空列表。
Future<List<String>> readLinesSafe(String path) async {
  final s = await readFileSafe(path);
  if (s == null) return const [];
  return const LineSplitter().convert(s);
}

/// 判断 [haystack] 是否包含任一关键字（不区分大小写）。
String? firstKeywordHit(String haystack, List<String> keywords) {
  final low = haystack.toLowerCase();
  for (final k in keywords) {
    if (low.contains(k.toLowerCase())) return k;
  }
  return null;
}

/// 从环境大小写混用的 map 中按 key 列表挑选第一个非空值。
String? pickEnv(Map<String, String> env, List<String> keys) {
  for (final k in keys) {
    final v = env[k] ?? env[k.toUpperCase()] ?? env[k.toLowerCase()];
    if (v != null && v.isNotEmpty && v != 'null' && v != '-1' && v != '0') {
      return v;
    }
  }
  return null;
}

/// 解析 `key=value` 形式的文本（如 /etc/os-release、gsettings），返回大小写无关 map。
Map<String, String> parseKeyValue(String text, {String separator = '='}) {
  final map = <String, String>{};
  for (final raw in const LineSplitter().convert(text)) {
    final line = raw.trim();
    if (line.isEmpty || line.startsWith('#')) continue;
    final idx = line.indexOf(separator);
    if (idx <= 0) continue;
    final key = line.substring(0, idx).trim();
    var value = line.substring(idx + 1).trim();
    if (value.length >= 2 &&
        ((value.startsWith('"') && value.endsWith('"')) ||
            (value.startsWith("'") && value.endsWith("'")))) {
      value = value.substring(1, value.length - 1);
    }
    map[key.toLowerCase()] = value;
  }
  return map;
}
