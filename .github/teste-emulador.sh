#!/bin/bash
# Teste automático: instala o ZEUS Optimizer no emulador e tira prints de cada aba.
set -x
P=com.zeus.optimizer
OUT=prints
mkdir -p "$OUT"

adb wait-for-device
adb shell settings put global window_animation_scale 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
adb install -r ZEUS-Optimizer.apk || { echo "FALHA NA INSTALACAO" > "$OUT/erro.txt"; exit 0; }

[ -f exemplo.apk ] && adb install -r exemplo.apk

# Permissões que o usuário daria na mão
adb shell cmd notification allow_dnd $P || true
adb shell pm grant $P android.permission.WRITE_SECURE_SETTINGS || true
adb shell appops set $P MANAGE_EXTERNAL_STORAGE allow
adb shell appops set $P GET_USAGE_STATS allow
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true

# Arquivos de exemplo para a limpeza encontrar
adb shell mkdir -p /sdcard/Download /sdcard/DCIM/.thumbnails /sdcard/DCIM/Camera /sdcard/Pictures/Vazia
adb shell "echo apk > /sdcard/Download/jogo-antigo.apk"
adb shell "echo tmp > /sdcard/Download/video.crdownload"
adb shell "echo log > /sdcard/Download/registro.log"
adb shell "dd if=/dev/urandom of=/sdcard/DCIM/.thumbnails/1.jpg bs=1024 count=300"
adb shell "dd if=/dev/urandom of=/sdcard/DCIM/Camera/foto.jpg bs=1024 count=900"
adb shell "cp /sdcard/DCIM/Camera/foto.jpg /sdcard/Download/foto-copia.jpg"
adb shell "dd if=/dev/urandom of=/sdcard/DCIM/Camera/video.mp4 bs=1024 count=4000"

adb logcat -c

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
abrir() { adb shell am start -S -n $P/.MainActivity --ei tab "$1" $2; sleep "$3"; }
rolar() { adb shell input swipe 540 1900 540 700 400; sleep 2; }
tocar_texto() {
  # procura o texto na tela; se não achar, rola e tenta de novo (até 10 vezes)
  for tentativa in $(seq 1 10); do
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
    if python3 - "$1" <<'PY'
import re, sys, subprocess
alvo = sys.argv[1]
xml = open("ui.xml", encoding="utf-8").read()
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if alvo in m.group(1):
        y1, y2 = int(m.group(3)), int(m.group(5))
        if y2 > 2150:  # escondido atrás da barra de navegação
            continue
        x = (int(m.group(2)) + int(m.group(4))) // 2
        subprocess.call(["adb", "shell", "input", "tap", str(x), str((y1 + y2) // 2)])
        print("tocou", m.group(1))
        sys.exit(0)
sys.exit(1)
PY
    then return 0; fi
    adb shell input swipe 540 1700 540 900 300; sleep 1
  done
  echo "nao achou: $1"
  return 1
}

abrir 0 "" 6;  shot 01-inicio
rolar;         shot 02-inicio-rolado
rolar;         shot 03-inicio-fim
abrir 0 "" 4
tocar_texto "OTIMIZAR AGORA"; sleep 3; shot 04-otimizando
sleep 20;      shot 05-otimizado

abrir 1 "" 6;  shot 06-turbo
rolar;         shot 07-turbo-2
rolar;         shot 08-turbo-3
rolar;         shot 09-turbo-4
abrir 1 "" 4
tocar_texto "Testar ping"; sleep 18; shot 10-ping
abrir 1 "" 4
tocar_texto "Iniciar benchmark"; sleep 4; shot 11-benchmark-rodando
sleep 22; shot 12-benchmark
abrir 1 "" 4
tocar_texto "Ativar DNS rápido"; sleep 3; shot 13-dns
echo "private_dns_mode: $(adb shell settings get global private_dns_mode) / $(adb shell settings get global private_dns_specifier)" >> "$OUT/verificacoes.txt"
tocar_texto "Rápida 0,5x"; sleep 3; shot 14-avancado
echo "animator_duration_scale depois do toque: $(adb shell settings get global animator_duration_scale)" >> "$OUT/verificacoes.txt"
abrir 1 "" 4
tocar_texto "Mostrar todos os apps"; sleep 2
tocar_texto "Jogar ⚡"; sleep 6; shot 15-jogo-aberto
echo "animacoes durante o jogo: $(adb shell settings get global animator_duration_scale)" >> "$OUT/verificacoes.txt"
adb shell am start -n $P/.MainActivity; sleep 2; shot 16-volta-do-jogo
echo "animacoes depois de voltar: $(adb shell settings get global animator_duration_scale)" >> "$OUT/verificacoes.txt"

abrir 2 "--ez autoscan true" 25; shot 17-limpeza
abrir 3 "" 15; shot 18-apps
abrir 4 "" 6;  shot 19-bateria
abrir 5 "" 4;  shot 20-mais
rolar;         shot 21-mais-rolado

adb logcat -d -b crash > "$OUT/crashes.txt"
adb logcat -d | grep -iE "AndroidRuntime|FATAL|zeus" | tail -200 > "$OUT/log.txt"
exit 0
