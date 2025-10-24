## 此库主要针对Android原生底层能力实现提供flutter方法，ios实现可能相对较少。

### 使用说明：

#### android支持

    const work = AndroidWork();
    print('--await AndroidWork.isProxy ${await work.isProxy()}');
    print('--await AndroidWork.isOpenVPN ${await work.isOpenVPN()}');

#### android/ios 都支持

    print('await AndroidWork.platformVersion ${await work.platformVersion}');
    print('await AndroidWork.deviceIMEINumber ${await work.deviceIMEINumber}');
    print('await AndroidWork.deviceModel ${await work.deviceModel}');
    print('await AndroidWork.deviceManufacturer ${await work.deviceManufacturer}');
    print('await AndroidWork.apiLevel ${await work.apiLevel}');
    print('await AndroidWork.deviceName ${await work.deviceName}');
    print('await AndroidWork.productName ${await work.productName}');
    print('await AndroidWork.cpuName ${await work.cpuName}');
    print('await AndroidWork.hardware ${await work.hardware}');