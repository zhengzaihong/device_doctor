import 'dart:convert';

import 'package:device_doctor/device_doctor.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

void main() => runApp(const MyApp());

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  static const doctor = DeviceDoctor();
  String _output = '点击下方按钮开始全量检测';
  bool _loading = false;

  Future<void> _runChecks() async {
    setState(() {
      _loading = true;
      _output = '检测中…';
    });

    // 电话权限仅 Android 有意义；桌面/Web 上 permission_handler 会抛
    // MissingPluginException，捕获后继续检测。
    try {
      final status = await Permission.phone.request();
      debugPrint('permission phone: $status');
    } catch (_) {
      debugPrint('permission phone not available on this platform');
    }

    try {
      final buf = StringBuffer();

      buf.writeln('platformVersion: ${await doctor.platformVersion}');
      buf.writeln('apiLevel: ${await doctor.apiLevel}');
      buf.writeln('deviceModel: ${await doctor.deviceModel}');
      buf.writeln('deviceManufacturer: ${await doctor.deviceManufacturer}');
      buf.writeln('deviceName: ${await doctor.deviceName}');
      buf.writeln('productName: ${await doctor.productName}');
      buf.writeln('cpuName: ${await doctor.cpuName}');
      buf.writeln('hardware: ${await doctor.hardware}');

      final imei = await doctor.deviceIMEINumber;
      buf.writeln('deviceIMEINumber: ${imei ?? "（不可用，Android 10+ 受限）"}');

      for (final t in ['SHA-1', 'SHA-256', 'MD5']) {
        final sigs = await doctor.getSignatures(t);
        buf.writeln('signatures($t): ${sigs.isEmpty ? "（无）" : jsonEncode(sigs)}');
      }

      final simInfo = await doctor.getSimulatorInfo();
      buf.writeln('getSimulatorInfo: ${simInfo.isEmpty ? "（无命中）" : jsonEncode(simInfo)}');
      final simRaw = await doctor.isSimulator();
      buf.writeln('isSimulator raw: ${jsonEncode(simRaw)}');
      buf.writeln('isEmulator(threshold=5): ${await doctor.isEmulator()}');
      buf.writeln('isEmulator(threshold=8): ${await doctor.isEmulator(threshold: 8)}');

      buf.writeln('isProxy: ${await doctor.isProxy()}');
      buf.writeln('isOpenVPN: ${await doctor.isOpenVPN()}');
      buf.writeln('isRootEnv: ${await doctor.isRootEnv()}');

      final out = buf.toString();
      debugPrint(out);
      if (!mounted) return;
      setState(() => _output = out);
    } catch (e, st) {
      debugPrint('checks failed: $e\n$st');
      if (!mounted) return;
      setState(() => _output = '检测失败: $e');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('device_doctor 示例')),
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _ActionButton(
                label: _loading ? '检测中…' : '一键检测全部环境项',
                onTap: _loading ? null : _runChecks,
              ),
              Row(
                children: [
                  Expanded(
                    child: _ActionButton(
                      label: '清空缓存',
                      onTap: _loading
                          ? null
                          : () async {
                              final messenger = ScaffoldMessenger.of(context);
                              await doctor.clearSimulatorCache();
                              if (!mounted) return;
                              messenger.showSnackBar(
                                const SnackBar(content: Text('已清空模拟器/包名/CPU 缓存')),
                              );
                            },
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _ActionButton(
                      label: '添加自定义指纹',
                      onTap: _loading
                          ? null
                          : () async {
                              final messenger = ScaffoldMessenger.of(context);
                              await doctor.addCustomEmulatorPackages(['com.example.myemu']);
                              if (!mounted) return;
                              messenger.showSnackBar(
                                const SnackBar(content: Text('已添加 com.example.myemu')),
                              );
                            },
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              SelectableText(
                _output,
                style: const TextStyle(fontSize: 12, fontFamily: 'monospace'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({required this.label, required this.onTap});
  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: ElevatedButton(
        onPressed: onTap,
        child: Text(label),
      ),
    );
  }
}
