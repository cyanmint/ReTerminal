#!/system/bin/sh

# Unified init-host script for ReTerminal
# This script handles all container modes (proot/chroot) with various options

# Parse arguments
MODE="proot"  # default: proot mode
UNSHARE="no"  # default: no unshare
SHARED_NS="no"  # default: no shared namespace
USE_SU="yes"  # default: use su

for arg in "$@"; do
    case "$arg" in
        --mode=*)
            MODE="${arg#*=}"
            ;;
        --unshare=*)
            UNSHARE="${arg#*=}"
            ;;
        --shared-ns=*)
            SHARED_NS="${arg#*=}"
            ;;
        --use-su=*)
            USE_SU="${arg#*=}"
            ;;
    esac
done

# Common setup for all modes
ALPINE_DIR=$PREFIX/local/alpine

mkdir -p $ALPINE_DIR

if [ -z "$(ls -A "$ALPINE_DIR" | grep -vE '^(root|tmp)$')" ]; then
    tar -xf "$PREFIX/files/alpine.tar.gz" -C "$ALPINE_DIR"
fi

if [ ! -d "$PREFIX/local/alpine/tmp" ]; then
    mkdir -p "$PREFIX/local/alpine/tmp"
    chmod 1777 "$PREFIX/local/alpine/tmp"
fi

# Helper function to execute commands with or without su
run_cmd() {
    if [ "$USE_SU" = "yes" ]; then
        su -c "$1"
    else
        sh -c "$1"
    fi
}

# Execute the appropriate mode
if [ "$MODE" = "proot" ]; then
    # ========== PROOT MODE ==========
    
    [ ! -e "$PREFIX/local/bin/proot" ] && cp "$PREFIX/files/proot" "$PREFIX/local/bin"
    
    for sofile in "$PREFIX/files/"*.so.2; do
        dest="$PREFIX/local/lib/$(basename "$sofile")"
        [ ! -e "$dest" ] && cp "$sofile" "$dest"
    done
    
    ARGS="--kill-on-exit"
    ARGS="$ARGS -w /"
    
    for system_mnt in /apex /odm /product /system /system_ext /vendor \
     /linkerconfig/ld.config.txt \
     /linkerconfig/com.android.art/ld.config.txt \
     /plat_property_contexts /property_contexts; do
    
     if [ -e "$system_mnt" ]; then
      system_mnt=$(realpath "$system_mnt")
      ARGS="$ARGS -b ${system_mnt}"
     fi
    done
    unset system_mnt
    
    ARGS="$ARGS -b /sdcard"
    ARGS="$ARGS -b /storage"
    ARGS="$ARGS -b /dev"
    ARGS="$ARGS -b /data"
    ARGS="$ARGS -b /dev/urandom:/dev/random"
    ARGS="$ARGS -b /proc"
    ARGS="$ARGS -b $PREFIX"
    ARGS="$ARGS -b $PREFIX/local/stat:/proc/stat"
    ARGS="$ARGS -b $PREFIX/local/vmstat:/proc/vmstat"
    
    if [ -e "/proc/self/fd" ]; then
      ARGS="$ARGS -b /proc/self/fd:/dev/fd"
    fi
    
    if [ -e "/proc/self/fd/0" ]; then
      ARGS="$ARGS -b /proc/self/fd/0:/dev/stdin"
    fi
    
    if [ -e "/proc/self/fd/1" ]; then
      ARGS="$ARGS -b /proc/self/fd/1:/dev/stdout"
    fi
    
    if [ -e "/proc/self/fd/2" ]; then
      ARGS="$ARGS -b /proc/self/fd/2:/dev/stderr"
    fi
    
    ARGS="$ARGS -b $PREFIX"
    ARGS="$ARGS -b /sys"
    
    ARGS="$ARGS -b $PREFIX/local/alpine/tmp:/dev/shm"
    
    ARGS="$ARGS -r $PREFIX/local/alpine"
    ARGS="$ARGS -0"
    ARGS="$ARGS --link2symlink"
    ARGS="$ARGS --sysvipc"
    ARGS="$ARGS -L"
    
    $LINKER $PREFIX/local/bin/proot $ARGS sh $PREFIX/local/bin/init "$@"

else
    # ========== CHROOT MODE ==========
    
    # Create necessary directories for bind mounts
    mkdir -p "$ALPINE_DIR/sdcard"
    mkdir -p "$ALPINE_DIR/storage"
    mkdir -p "$ALPINE_DIR/dev"
    mkdir -p "$ALPINE_DIR/proc"
    mkdir -p "$ALPINE_DIR/sys"
    mkdir -p "$ALPINE_DIR$PREFIX"
    
    # Create linkerconfig directories if needed
    if [ -e /linkerconfig/ld.config.txt ]; then
        mkdir -p "$ALPINE_DIR/linkerconfig"
    fi
    
    if [ -e /linkerconfig/com.android.art/ld.config.txt ]; then
        mkdir -p "$ALPINE_DIR/linkerconfig/com.android.art"
    fi
    
    # Create property context files if needed
    if [ -e /plat_property_contexts ]; then
        touch "$ALPINE_DIR/plat_property_contexts" 2>/dev/null || true
    fi
    
    if [ -e /property_contexts ]; then
        touch "$ALPINE_DIR/property_contexts" 2>/dev/null || true
    fi
    
    # Create system mount points
    for system_mnt in /apex /odm /product /system /system_ext /vendor; do
        if [ -e "$system_mnt" ]; then
            mkdir -p "$ALPINE_DIR$system_mnt"
        fi
    done
    
    # Define mount commands as a variable to avoid repetition
    MOUNT_CMDS='
        mount -t proc proc "'"$ALPINE_DIR"'/proc" 2>/dev/null || true
        mount --bind /sdcard "'"$ALPINE_DIR"'/sdcard" 2>/dev/null || true
        mount --bind /storage "'"$ALPINE_DIR"'/storage" 2>/dev/null || true
        mount --bind /dev "'"$ALPINE_DIR"'/dev" 2>/dev/null || true
        mount --bind /sys "'"$ALPINE_DIR"'/sys" 2>/dev/null || true
        mount --bind "'"$PREFIX"'" "'"$ALPINE_DIR$PREFIX"'" 2>/dev/null || true
        mount --bind "'"$PREFIX"'/local/stat" "'"$ALPINE_DIR"'/proc/stat" 2>/dev/null || true
        mount --bind "'"$PREFIX"'/local/vmstat" "'"$ALPINE_DIR"'/proc/vmstat" 2>/dev/null || true
        mount --bind "'"$PREFIX"'/local/alpine/tmp" "'"$ALPINE_DIR"'/dev/shm" 2>/dev/null || true
        
        for system_mnt in /apex /odm /product /system /system_ext /vendor; do
            if [ -e "$system_mnt" ]; then
                mount --bind "$system_mnt" "'"$ALPINE_DIR"'$system_mnt" 2>/dev/null || true
            fi
        done
        
        if [ -e /linkerconfig/ld.config.txt ]; then
            mount --bind /linkerconfig/ld.config.txt "'"$ALPINE_DIR"'/linkerconfig/ld.config.txt" 2>/dev/null || true
        fi
        
        if [ -e /linkerconfig/com.android.art/ld.config.txt ]; then
            mount --bind /linkerconfig/com.android.art/ld.config.txt "'"$ALPINE_DIR"'/linkerconfig/com.android.art/ld.config.txt" 2>/dev/null || true
        fi
        
        if [ -e /plat_property_contexts ]; then
            mount --bind /plat_property_contexts "'"$ALPINE_DIR"'/plat_property_contexts" 2>/dev/null || true
        fi
        
        if [ -e /property_contexts ]; then
            mount --bind /property_contexts "'"$ALPINE_DIR"'/property_contexts" 2>/dev/null || true
        fi
        
        mount --bind /dev/urandom "'"$ALPINE_DIR"'/dev/random" 2>/dev/null || true
        
        if [ -e /proc/self/fd ]; then
            mount --bind /proc/self/fd "'"$ALPINE_DIR"'/dev/fd" 2>/dev/null || true
        fi
        
        if [ -e /proc/self/fd/0 ]; then
            mount --bind /proc/self/fd/0 "'"$ALPINE_DIR"'/dev/stdin" 2>/dev/null || true
        fi
        
        if [ -e /proc/self/fd/1 ]; then
            mount --bind /proc/self/fd/1 "'"$ALPINE_DIR"'/dev/stdout" 2>/dev/null || true
        fi
        
        if [ -e /proc/self/fd/2 ]; then
            mount --bind /proc/self/fd/2 "'"$ALPINE_DIR"'/dev/stderr" 2>/dev/null || true
        fi
    '
    
    if [ "$UNSHARE" = "no" ]; then
        # ========== CHROOT WITHOUT NAMESPACES ==========
        run_cmd "$MOUNT_CMDS
            exec chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\"
        "
        
    elif [ "$SHARED_NS" = "yes" ]; then
        # ========== CHROOT WITH SHARED NAMESPACE ==========
        NAMESPACE_PID_FILE=$PREFIX/local/.alpine-ns-pid
        NAMESPACE_CREATION_WAIT=0.5
        
        # Check if namespace already exists and is valid
        NS_EXISTS=0
        if [ -f "$NAMESPACE_PID_FILE" ]; then
            NS_PID=$(cat "$NAMESPACE_PID_FILE" 2>/dev/null)
            if [ -n "$NS_PID" ]; then
                # Check if the process still exists
                if run_cmd "kill -0 $NS_PID 2>/dev/null"; then
                    NS_EXISTS=1
                else
                    # Process is dead, remove stale PID file
                    rm -f "$NAMESPACE_PID_FILE"
                fi
            fi
        fi
        
        if [ "$NS_EXISTS" = "1" ]; then
            # Namespace exists, enter it using nsenter (subsequent sessions)
            run_cmd "NSENTER_MODE=1 nsenter -t $NS_PID -m -p -u -i chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\""
        else
            # Create new namespace with init as PID 1 (first session)
            run_cmd "
                unshare -a -f sh -c '
                    $MOUNT_CMDS
                    
                    # Save PID to file for future sessions
                    echo \$\$ > \"$NAMESPACE_PID_FILE\"
                    
                    # Execute init as PID 1 in the namespace
                    exec chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\"
                '
            " &
            
            # Wait for namespace to be created
            sleep $NAMESPACE_CREATION_WAIT
            
            # First session: Enter the newly created namespace
            if [ -f "$NAMESPACE_PID_FILE" ]; then
                NS_PID=$(cat "$NAMESPACE_PID_FILE" 2>/dev/null)
                if [ -n "$NS_PID" ]; then
                    if run_cmd "kill -0 $NS_PID 2>/dev/null"; then
                        run_cmd "NSENTER_MODE=1 nsenter -t $NS_PID -m -p -u -i chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\""
                    fi
                fi
            fi
        fi
        
    else
        # ========== CHROOT WITH ISOLATED NAMESPACES ==========
        run_cmd "
            unshare -a -f sh -c '
                $MOUNT_CMDS
                exec chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\"
            '
        "
    fi
fi
