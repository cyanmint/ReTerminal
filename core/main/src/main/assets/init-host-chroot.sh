#!/system/bin/sh

ALPINE_DIR=$PREFIX/local/alpine

mkdir -p $ALPINE_DIR

if [ -z "$(ls -A "$ALPINE_DIR" | grep -vE '^(root|tmp)$')" ]; then
    tar -xf "$PREFIX/files/alpine.tar.gz" -C "$ALPINE_DIR"
fi

if [ ! -d "$PREFIX/local/alpine/tmp" ]; then
 mkdir -p "$PREFIX/local/alpine/tmp"
 chmod 1777 "$PREFIX/local/alpine/tmp"
fi

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

# Determine if we should use su based on USE_SU environment variable
USE_SU_CMD=""
if [ "$USE_SU" != "0" ]; then
    USE_SU_CMD="su -c"
fi

# Create new namespaces with init as PID 1 (automatic with --mount-proc):
#   -m: mount namespace (isolated filesystem mounts)
#   -p: PID namespace (isolated process tree, init will be PID 1)
#   -u: UTS namespace (isolated hostname)
#   -i: IPC namespace (isolated inter-process communication)
#   -f: fork before executing command (required for PID namespace)
#   --mount-proc: mount proc in the new PID namespace (init becomes PID 1)
$USE_SU_CMD "
    # Create new namespaces with proper PID namespace
    unshare -m -p -u -i -f --mount-proc=\"$ALPINE_DIR/proc\" sh -c '
        # Bind mount necessary directories
        mount --bind /sdcard \"$ALPINE_DIR/sdcard\" 2>/dev/null || true
        mount --bind /storage \"$ALPINE_DIR/storage\" 2>/dev/null || true
        mount --bind /dev \"$ALPINE_DIR/dev\" 2>/dev/null || true
        mount --bind /sys \"$ALPINE_DIR/sys\" 2>/dev/null || true
        mount --bind \"$PREFIX\" \"$ALPINE_DIR$PREFIX\" 2>/dev/null || true
        mount --bind \"$PREFIX/local/stat\" \"$ALPINE_DIR/proc/stat\" 2>/dev/null || true
        mount --bind \"$PREFIX/local/vmstat\" \"$ALPINE_DIR/proc/vmstat\" 2>/dev/null || true
        
        # Bind mount /dev/shm
        mount --bind \"$PREFIX/local/alpine/tmp\" \"$ALPINE_DIR/dev/shm\" 2>/dev/null || true
        
        # Bind mount system directories
        for system_mnt in /apex /odm /product /system /system_ext /vendor; do
            if [ -e \"\$system_mnt\" ]; then
                mount --bind \"\$system_mnt\" \"$ALPINE_DIR\$system_mnt\" 2>/dev/null || true
            fi
        done
        
        # Bind mount linkerconfig if present
        if [ -e /linkerconfig/ld.config.txt ]; then
            mount --bind /linkerconfig/ld.config.txt \"$ALPINE_DIR/linkerconfig/ld.config.txt\" 2>/dev/null || true
        fi
        
        if [ -e /linkerconfig/com.android.art/ld.config.txt ]; then
            mount --bind /linkerconfig/com.android.art/ld.config.txt \"$ALPINE_DIR/linkerconfig/com.android.art/ld.config.txt\" 2>/dev/null || true
        fi
        
        # Bind mount property contexts
        if [ -e /plat_property_contexts ]; then
            mount --bind /plat_property_contexts \"$ALPINE_DIR/plat_property_contexts\" 2>/dev/null || true
        fi
        
        if [ -e /property_contexts ]; then
            mount --bind /property_contexts \"$ALPINE_DIR/property_contexts\" 2>/dev/null || true
        fi
        
        # Bind mount /dev/random
        mount --bind /dev/urandom \"$ALPINE_DIR/dev/random\" 2>/dev/null || true
        
        # Set up /dev/fd, stdin, stdout, stderr
        if [ -e /proc/self/fd ]; then
            mount --bind /proc/self/fd \"$ALPINE_DIR/dev/fd\" 2>/dev/null || true
        fi
        
        if [ -e /proc/self/fd/0 ]; then
            mount --bind /proc/self/fd/0 \"$ALPINE_DIR/dev/stdin\" 2>/dev/null || true
        fi
        
        if [ -e /proc/self/fd/1 ]; then
            mount --bind /proc/self/fd/1 \"$ALPINE_DIR/dev/stdout\" 2>/dev/null || true
        fi
        
        if [ -e /proc/self/fd/2 ]; then
            mount --bind /proc/self/fd/2 \"$ALPINE_DIR/dev/stderr\" 2>/dev/null || true
        fi
        
        # Change root and execute init script
        exec chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\"
    '
"
