# `Rangel` 仿真救援 安徽理工大学队伍代码

![avatar](https://www.aust.edu.cn/__local/8/7B/80/638D1607174C29D4831915BA71E_01FFC6E0_D2E6.png)

## 1. 环境要求

* Git
* OpenJDK Java 17
* Gradle

## 2. 下载

```bash
$ git clone git@github.com:roborescue-aust/Rangel.git
```

## 3. 编译

```bash
$ ./gradlew build
```

## 4. 执行

`Rangel`是使用ADF框架 (`adf-core-java`) 的RCRS (`rcrs-server`) 的队伍代码实现.

要运行 `Rangel`，首先必须运行 `rcrs-server`（有关如何下载、编译和运行 `rcrs-server` 的说明可在 <https://github.com/roborescue/rcrs-server> 获得）。

启动 `rcrs-server` 后，打开一个新的终端窗口并执行


预计算启动
```bash
$ bash launch.sh -pre 1 -t 1,0,1,0,1,0 -local&&PID=$$;sleep 120;kill $PID
```

正常模式启动
```bash
$ bash launch.sh -all
```

## 5. 支持

要报告错误、建议改进或请求支持，请在 GitHub <https://github.com/roborescue-aust/Rangel/issues> 上提出issue.


## 6. conventional commits 约定式提交

参考 [约定式提交官网](https://www.conventionalcommits.org/zh-hans/v1.0.0/)

| Commit Type | Title                    | Description                                                                                                 | 
|:-----------:|--------------------------|-------------------------------------------------------------------------------------------------------------|
|   `feat`    | Features                 | A new feature                                                                                               |
|    `fix`    | Bug Fixes                | A bug Fix                                                                                                   |
|   `docs`    | Documentation            | Documentation only changes                                                                                  |
|   `style`   | Styles                   | Changes that do not affect the meaning of the code (white-space, formatting, missing semi-colons, etc)      |
| `refactor`  | Code Refactoring         | A code change that neither fixes a bug nor adds a feature                                                   |
|   `perf`    | Performance Improvements | A code change that improves performance                                                                     |
|   `test`    | Tests                    | Adding missing tests or correcting existing tests                                                           |
|   `build`   | Builds                   | Changes that affect the build system or external dependencies (example scopes: gulp, broccoli, npm)         |
|    `ci`     | Continuous Integrations  | Changes to our CI configuration files and scripts (example scopes: Travis, Circle, BrowserStack, SauceLabs) |
|   `chore`   | Chores                   | Other changes that don't modify src or test files                                                           |
|  `revert`   | Reverts                  | Reverts a previous commit                                                                                   |

### 关于git cz
本项目使用 [git cz](https://github.com/streamich/git-cz) 来规范化提交信息,相关配置在`changelog.config.js`文件中
1. 使用前请先安装Node.js后运行以下命令全局安装git cz
```bash
npm install -g git-cz
```
2. 在`git add .`后，项目根路径下使用`git cz`根据提示填写信息，即可提交本地仓库

