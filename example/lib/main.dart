import 'dart:convert';

import 'package:android_work/android_work.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

void main() => runApp(const MyApp());

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  static const work = AndroidWork();
  String _output = '点击下方按钮开始检测';
  bool _loading = false;

  Future<void> _runChecks() async {
    setState(() {
      _loading = true;
      _output = '请求权限并检测中…';
    });

    final status = await Permission.phone.request();
    debugPrint('permission phone: $status');

    try {
      final buf = StringBuffer();

      buf.writeln('platformVersion: ${await work.platformVersion}');
      buf.writeln('apiLevel: ${await work.apiLevel}');
      buf.writeln('deviceModel: ${await work.deviceModel}');
      buf.writeln('deviceManufacturer: ${await work.deviceManufacturer}');
      buf.writeln('deviceName: ${await work.deviceName}');
      buf.writeln('productName: ${await work.productName}');
      buf.writeln('cpuName: ${await work.cpuName}');
      buf.writeln('hardware: ${await work.hardware}');

      final imei = await work.deviceIMEINumber;
      buf.writeln('deviceIMEINumber: ${imei ?? "<不可用，Android 10+ 受限>"}');

      for (final t in ['SHA-1', 'SHA-256', 'MD5']) {
        final sigs = await work.getSignatures(t);
        buf.writeln('signatures($t): ${sigs.isEmpty ? "<空>" : jsonEncode(sigs)}');
      }

      final simInfo = await work.getSimulatorInfo();
      buf.writeln('getSimulatorInfo: ${simInfo.isEmpty ? "<未检出>" : jsonEncode(simInfo)}');
      final simRaw = await work.isSimulator();
      buf.writeln('isSimulator raw: ${jsonEncode(simRaw)}');
      buf.writeln('isEmulator(threshold=4): ${await work.isEmulator()}');
      buf.writeln('isEmulator(threshold=8): ${await work.isEmulator(threshold: 8)}');

      buf.writeln('isProxy: ${await work.isProxy()}');
      buf.writeln('isOpenVPN: ${await work.isOpenVPN()}');
      buf.writeln('isRootEnv: ${await work.isRootEnv()}');

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
        appBar: AppBar(title: const Text('android_work 示例')),
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _ActionButton(
                label: _loading ? '检测中…' : '一键检测（权限→全部能力）',
                onTap: _loading ? null : _runChecks,
              ),
              Row(
                children: [
                  Expanded(
                    child: _ActionButton(
                      label: '清空原生缓存',
                      onTap: _loading
                          ? null
                          : () async {
                              final messenger = ScaffoldMessenger.of(context);
                              await work.clearSimulatorCache();
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
                              await work.addCustomEmulatorPackages(['com.example.myemu']);
                              if (!mounted) return;
                              messenger.showSnackBar(
                                const SnackBar(content: Text('已注册 com.example.myemu')),
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
