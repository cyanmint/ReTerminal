set -e  # Exit immediately on Failure

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin
export HOME=/root

if [ ! -s /etc/resolv.conf ]; then
    echo "nameserver 8.8.8.8" > /etc/resolv.conf
fi


export PS1="\[\e[38;5;46m\]\u\[\033[39m\]@reterm \[\033[39m\]\w \[\033[0m\]\\$ "
# shellcheck disable=SC2034
export PIP_BREAK_SYSTEM_PACKAGES=1
required_packages="bash gcompat glib nano"
missing_packages=""
for pkg in $required_packages; do
    if ! apk info -e $pkg >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done
if [ -n "$missing_packages" ]; then
    echo -e "\e[34;1m[*] \e[0mInstalling Important packages\e[0m"
    apk update && apk upgrade
    apk add $missing_packages
    if [ $? -eq 0 ]; then
        echo -e "\e[32;1m[+] \e[0mSuccessfully Installed\e[0m"
    fi
    echo -e "\e[34m[*] \e[0mUse \e[32mapk\e[0m to install new packages\e[0m"
fi

#fix linker warning
if [[ ! -f /linkerconfig/ld.config.txt ]];then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi

if [ "$#" -eq 0 ]; then
    source /etc/profile
    export PS1="\[\e[38;5;46m\]\u\[\033[39m\]@reterm \[\033[39m\]\w \[\033[0m\]\\$ "
    cd $HOME
    
    # Check if we entered an existing namespace (not PID 1)
    # If so, disown the shell to make its parent the container init
    if [ -n "$NSENTER_MODE" ] && [ "$NSENTER_MODE" = "1" ]; then
        # Check if init process exists as PID 1
        if [ -d "/proc/1" ] && grep -q "init" /proc/1/comm 2>/dev/null; then
            # Launch shell in background and disown it
            # This makes the shell's parent the init process (PID 1)
            /bin/ash &
            disown
            # Exit this script so shell gets reparented
            exit 0
        fi
    fi
    
    # Normal mode: exec shell directly
    /bin/ash
else
    exec "$@"
fi