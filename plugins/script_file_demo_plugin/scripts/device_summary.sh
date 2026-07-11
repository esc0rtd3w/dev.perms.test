#!/system/bin/sh
printf 'script_file_demo=ok\n'
printf 'model='; getprop ro.product.model
printf 'sdk='; getprop ro.build.version.sdk
printf 'abi='; getprop ro.product.cpu.abi
printf 'user='; id 2>/dev/null || true
