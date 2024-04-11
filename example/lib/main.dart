import 'dart:convert';

import 'package:android_work_forzzh/android_work.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body:Center(
          child: Column(

            children: [

              buildButton("获取设备信息", () async {
                List<Permission> permissions = <Permission>[
                  Permission.phone
                ];

                for (var element in permissions) {
                  element.request().then((value) async {
                    const work = AndroidWork();

                    work.isRootEnv().then((value){
                      print("------------------root:$value");
                    });

                    // work.isSimulator().then((value){
                    //   print("------------------value:${jsonEncode(value)}");
                    //   final isSimulator = value["value"]>3;
                    //     print("-----------------isSimulator:$isSimulator");
                    // });

                    // print('--await AndroidWork.isSimulator ${await work.isSimulator()}');
                    // print('--await AndroidWork.getSimulatorInfo ${await work.getSimulatorInfo()}');
                    //
                    //
                    //
                    // print('--await AndroidWork.isProxy ${await work.isProxy()}');
                    // print('--await AndroidWork.isOpenVPN ${await work.isOpenVPN()}');
                    //
                    // print('--await AndroidWork.platformVersion ${await work.platformVersion}');
                    // print('--await AndroidWork.deviceIMEINumber ${await work.deviceIMEINumber}');
                    // print('--await AndroidWork.deviceModel ${await work.deviceModel}');
                    // print('--await AndroidWork.deviceManufacturer ${await work.deviceManufacturer}');
                    // print('--await AndroidWork.apiLevel ${await work.apiLevel}');
                    // print('--await AndroidWork.deviceName ${await work.deviceName}');
                    // print('--await AndroidWork.productName ${await work.productName}');
                    // print('--await AndroidWork.cpuName ${await work.cpuName}');
                    // print('--await AndroidWork.hardware ${await work.hardware}');
                  });
                }


              }),
            ],
          ),
        ),
      ),
    );
  }

  Widget buildButton(String title,Function function){

    return GestureDetector(
      onTap: (){
        function.call();
      },
      child:  Container(
        width: 200,
        height: 35,
        margin: const EdgeInsets.only(top: 20),
        alignment: Alignment.center,
        decoration: const BoxDecoration(
            color: Colors.lightBlueAccent,
            borderRadius: BorderRadius.all(Radius.circular(10))
        ),
        child: Text(title),
      ),
    );
  }

}
