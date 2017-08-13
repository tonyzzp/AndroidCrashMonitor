[![](https://jitpack.io/v/tonyzzp/AndroidCrashMonitor.svg)](https://jitpack.io/#tonyzzp/AndroidCrashMonitor)

### 这是什么
在程序crash时会记录下日志。然后可以引导用户使用邮件的方式将日志发送给您。适用于那些没有添加网络权限的应用。

### 如何使用

#### 在根目录的build.gradle文件内添加

	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}

#### 在module目录内的build.gradle文件内添加

	dependencies {
		compile 'com.github.tonyzzp:AndroidCrashMonitor:0.1'
	}

#### 初始化

	CrashMonitor.Config cfg = new CrashMonitor.Config();
	cfg.email = "myemail@qq.com";
	CrashMonitor.init(this, cfg);

#### 上报日志

    if (CrashMonitor.hasCrashLogs() && CrashMonitor.shouldSendLog()) {
        CrashMonitor.showReportDialog(this, null);
    }

#### 增加自定义的上传内容

    CrashMonitor.setLogProvider(new CrashMonitor.LogProvider() {
        @Override
        public String provide() {
            return "mylog:中文:" + this.getClass().toString();
        }
    });

