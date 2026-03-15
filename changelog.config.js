module.exports = {
    disableEmoji: false,
    format: '{type}{scope}: {emoji}{subject}',
    list: ['revert','build','test', 'feat', 'fix', 'chore', 'docs', 'refactor', 'style', 'ci', 'perf'],
    maxMessageLength: 64,
    minMessageLength: 3,
    // questions: ['type', 'scope', 'subject', 'body', 'breaking', 'issues', 'lerna'],
    questions: ['type', 'scope', 'subject', 'body'],
    scopes: ["rangel","centralized","extaction","module","utils"],
    types: {
        build:{
            description: 'Changes that affect the build system or external dependencies (影响构建系统或外部依赖关系的更改)',
            emoji: '🛠️',
            value: 'build'
        },
        revert:{
            description: 'Reverts a previous commit (还原上一次提交)',
            emoji: '⏪️',
            value: 'revert'
        },
        chore: {
            description: 'Other changes that don\'t modify src or test files (不修改src或测试文件的更改)',
            emoji: '🔧',
            value: 'chore'
        },
        ci: {
            description: 'Continuous Integrations (持续集成配置文件和脚本的更改)',
            emoji: '👷',
            value: 'ci'
        },
        docs: {
            description: 'Documentation only changes (仅修改文档/注释)',
            emoji: '📝',
            value: 'docs'
        },
        feat: {
            description: 'A new feature (添加新特性)',
            emoji: '✨',
            value: 'feat'
        },
        fix: {
            description: 'A bug fix (修复bug)',
            emoji: '🐛',
            value: 'fix'
        },
        perf: {
            description: 'A code change that improves performance (性能改进)',
            emoji: '⚡️',
            value: 'perf'
        },
        refactor: {
            description: 'A code change that neither fixes a bug or adds a feature (重构代码，既没有修复错误也没有添加功能的代码更改)',
            emoji: '♻️',
            value: 'refactor'
        },
        release: {
            description: 'Create a release commit (创建一个发行版提交)',
            emoji: '🔖',
            value: 'release'
        },
        style: {
            description: 'Changes that do not affect the meaning of the code (不影响代码含义的更改)',
            emoji: '🎨',
            value: 'style'
        },
        test: {
            description: 'Adding missing tests or correcting existing tests (添加缺失的测试或更正现有的测试)',
            emoji: '✅',
            value: 'test'
        }
    },
    messages: {
        type: '选择要提交的更改类型(必要):',
        scope: '选择此提交影响的范围:',
        subject: '写一个简短的、祈使语气的描述(不少于三个字):\n',
        body: '提供较长的更改说明(回车默认为空):\n ',
        // breaking: '列出任何破坏性更改:\n',
        // footer: 'Issues this commit closes, e.g #123:',
        // confirmCommit: '此提交影响的包\n',
    },
};