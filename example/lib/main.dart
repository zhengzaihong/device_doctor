import 'package:android_work_forzzh/android_work.dart';
import 'package:flutter/material.dart';

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

              buildButton("获取进程", (){
                const work = AndroidWork();
                work.getRunningAppProcesses().then((value){
                  print('-------------------11111111----------');
                  print(value);
                  print('--------------------22222222---------');
                });
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
