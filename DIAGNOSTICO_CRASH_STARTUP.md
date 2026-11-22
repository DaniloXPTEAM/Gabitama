# Diagnóstico de abertura — R1.4.2, investigação parcial

**ROOT_CAUSE_NOT_CONFIRMED.** A declaração do proprietário não foi acompanhada de log. Não há exceção, stack trace, versão Android ou cenário de instalação observados aqui. Nenhuma correção causal ou teste de regressão específico é possível sem reprodução. Homologação bloqueada. A aprovação anterior baseada em testes JVM e integridade foi prematura.

Inspeção estática da base 0ecdbe5 (não é diagnóstico): manifest declara Application `.app.NovoAgenteApplication`, Activity exportada `.app.MainActivity`, MAIN/LAUNCHER e FileProvider `${applicationId}.fileprovider`; debug aplica sufixo `.debug`, minSdk 26, targetSdk 34, Java 17. Application Hilt injeta TaskOrchestrator e HBCharacterController antes da primeira tela; Activity é AndroidEntryPoint e usa Compose/NavGraph. O controller resolve assets e observa preferências no startup; HomeViewModel injeta orquestrador, voz e DAOs e inicia busca de clima. NavGraph aguarda DataStore e inicia em Permissões ou Home. Room usa `fallbackToDestructiveMigration()` (risco de perda de dados em migração sem rota; não demonstrado como causa). Media3 ExoPlayer é criado em componentes de avatar. Nenhum desses fatos demonstra crash.

## Coleta no Windows com autorização do proprietário
Instale Android SDK Platform Tools oficial, habilite depuração USB, conecte e autorize o computador. Não limpe dados nem desinstale. No Prompt de Comando, na pasta platform-tools:

```bat
adb devices -l
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
adb shell pm path com.novoagentehb.app.debug
adb logcat -c
adb shell am start -W -n com.novoagentehb.app.debug/com.novoagentehb.app.app.MainActivity
adb shell pidof com.novoagentehb.app.debug
adb logcat -d -b crash -v time > crash_privado.txt
adb logcat -d -v time > startup_privado.txt
```

`logcat -c` limpa o buffer de logs do aparelho, não os dados do aplicativo; faça apenas com consentimento. Confirme primeiro se o pacote debug está instalado: caso contrário consulte `adb shell pm list packages | findstr novoagente`. Informe o erro exato de instalação, versão Android, modelo, arquitetura, armazenamento e se há outra assinatura/versão instalada; não desinstale para resolver conflito sem avaliar perda de dados. Se abre só em instalação limpa, comparar atualização com dados preservados em aparelho de teste. Envie logs exclusivamente por canal privado, revistos para remover credenciais, números, mensagens e dados pessoais; preserve linhas FATAL EXCEPTION, Process e Caused by. Não publique log bruto no GitHub.
