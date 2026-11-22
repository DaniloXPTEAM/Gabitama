# Testes Android — bloqueados

Base 0ecdbe500648671d5eb2c02a81cd18af9d9c6ff4; alterações locais ainda não publicadas. Neste sandbox: `command -v adb`, `command -v emulator`, `command -v java` retornaram ausentes; `/dev/kvm` ausente. SDK/AVD e aparelho não disponíveis. Nenhuma execução Android ocorreu, nenhuma captura real existe.

| Estado | Resultado | Evidência |
|---|---|---|
| ZIP/APK_INTEGRITY | NOT_TESTED | nenhum APK obtido aqui |
| BUILD / APK_BUILT | NOT_TESTED | Java e SDK ausentes |
| UNIT | NOT_TESTED | não executado nesta revisão; 28 anteriores foram reportados pelo solicitante, não verificados aqui |
| INSTALL / LAUNCH / NAVIGATION / FUNCTION | NOT_TESTED | sem Android |
| DEVICE | NOT_TESTED | aparelho não conectado |

Contagens nesta revisão: unitários 0 executados, instrumentados 0 executados, funcionais UI 0 executados; logo 0 pass e 0 fail em cada categoria, não sucesso. Um teste instrumentado novo foi escrito, não compilado nem executado. Propõe verificar Continuar, Home, 8 segundos RESUMED, Chat e retorno; não cobre os demais requisitos. Não verifica HB infantil ou diálogos. Nenhuma ação externa é acionada pelo teste.

O job API 34 é proposta não validada; falha impede seu sucesso, mas não deve ser lido como homologação integral. Executar pelo proprietário somente após aplicar as alterações no próprio repositório e revisar o workflow. Separar resultado do job de build e do job do emulador. Após API 34 bem-sucedida, executar API 29/26, atualização sem limpeza, todas as telas e o restante da matriz. Coletar log completo em ambiente privado; o workflow deliberadamente não publica logcat bruto. Em caso de falha, não há stack pública automática; reprodução privada e sanitização humana são necessárias.
