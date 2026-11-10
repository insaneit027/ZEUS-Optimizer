#!/bin/bash
# Teste automático: instala o ZEUS Optimizer no emulador e tira prints de cada aba.
set -x
P=com.zeus.optimizer
OUT=prints
mkdir -p "$OUT"

adb wait-for-device
adb shell settings put global window_animation_scale 1
adb install -r ZEUS-Optimizer.apk || { echo "FALHA NA INSTALACAO" > "$OUT/erro.txt"; exit 0; }

[ -f exemplo.apk ] && adb install -r exemplo.apk

# Permissões que o usuário daria na mão
adb shell cmd notification allow_dnd $P || true
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
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  python3 - "$1" <<'PY'
import re, sys, subprocess
alvo = sys.argv[1]
xml = open("ui.xml", encoding="utf-8").read()
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if alvo in m.group(1):
        x = (int(m.group(2)) + int(m.group(4))) // 2
        y = (int(m.group(3)) + int(m.group(5))) // 2
        subprocess.call(["adb", "shell", "input", "tap", str(x), str(y)])
        print("tocou", m.group(1), x, y)
        break
else:
    print("nao achou", alvo)
PY
}

abrir 0 "" 6;  shot 01-inicio
rolar;         shot 02-inicio-rolado
rolar;         shot 03-inicio-fim
abrir 0 "" 4
tocar_texto "OTIMIZAR AGORA"; sleep 3; shot 04-otimizando
sleep 20;      shot 05-otimizado

abrir 1 "--ez autoscan true" 25; shot 06-limpeza
rolar;         shot 07-limpeza-rolado
rolar;         shot 08-limpeza-fim

abrir 2 "" 15; shot 09-apps
rolar;         shot 10-apps-rolado

abrir 3 "" 6;  shot 11-bateria
rolar;         shot 12-bateria-rolado

abrir 4 "" 4;  shot 13-ferramentas
rolar;         shot 14-ferramentas-rolado
abrir 4 "" 4
tocar_texto "Ativar Modo Jogo"; sleep 4; shot 15-modo-jogo
tocar_texto "Desativar Modo Jogo"; sleep 2

adb logcat -d -b crash > "$OUT/crashes.txt"
adb logcat -d | grep -iE "AndroidRuntime|FATAL|zeus" | tail -200 > "$OUT/log.txt"
exit 0
