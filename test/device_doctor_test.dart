import 'package:device_doctor/device_doctor.dart';
import 'package:flutter_test/flutter_test.dart';

class _FakeDoctor extends DeviceDoctorPlatform {
  @override
  Future<Map<String, dynamic>> isSimulator() async => const {
        'value': 3,
        'hardwareHit': '',
      };

  @override
  Future<String> getPlatformVersion() async => 'test';
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('isEmulator honors hardware hit and threshold', () async {
    final old = DeviceDoctorPlatform.instance;
    addTearDown(() => DeviceDoctorPlatform.instance = old);

    DeviceDoctorPlatform.instance = _FakeDoctor();
    const doctor = DeviceDoctor();
    expect(await doctor.isEmulator(), isFalse);
    expect(await doctor.isEmulator(threshold: 3), isTrue);
  });

  test('desktop result exposes Android-compatible score fields', () async {
    final result = await DeviceDoctorLinux().isSimulator();
    expect(result, containsPair('platform', 'linux'));
    expect(result['value'], isA<int>());
    expect(result, contains('hardwareHit'));
    expect(result['hits'], isA<List<dynamic>>());
  });

  test('custom emulator entries increase score and can be cleared', () async {
    final doctor = DeviceDoctorLinux();
    final before = await doctor.isSimulator();
    await doctor.addCustomEmulatorPackages(const ['com.example.virtual-device']);
    final after = await doctor.isSimulator();
    expect(after['value'], greaterThan(before['value'] as int));
    await doctor.clearSimulatorCache();
    expect(await doctor.getSimulatorInfo(), isA<List<String>>());
  });

  test('desktop signatures are empty because APK signing is Android-only', () async {
    expect(await DeviceDoctorLinux().getSignatures('SHA-256'), isEmpty);
  });
}
