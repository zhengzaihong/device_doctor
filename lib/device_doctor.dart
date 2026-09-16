library device_doctor;

export 'src/device_doctor.dart';
export 'src/device_doctor_platform_interface.dart';
export 'src/device_doctor_method_channel.dart';
export 'src/device_doctor_desktop_base.dart';
export 'src/device_doctor_windows.dart';
export 'src/device_doctor_linux.dart';
export 'src/device_doctor_macos.dart';

// 兼容别名：老包名 `android_work` 的导入仍可工作
export 'src/android_work_compat.dart' show AndroidWork;

export 'src/device_doctor_platform_interface.dart' show DeviceDoctorPlatform;
export 'src/device_doctor_method_channel.dart' show MethodChannelDeviceDoctor;
