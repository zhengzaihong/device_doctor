import 'package:flutter_test/flutter_test.dart';

import 'package:device_doctor_example/main.dart';

void main() {
  testWidgets('App builds and shows entry button', (WidgetTester tester) async {
    await tester.pumpWidget(const MyApp());

    expect(find.text('device_doctor 示例'), findsOneWidget);
    expect(find.textContaining('一键检测'), findsOneWidget);
  });
}
