#!/system/bin/sh

ALPINE_DIR=$PREFIX/local/alpine
NAMESPACE_PID_FILE=$PREFIX/local/.alpine-ns-pid
NAMESPACE_CREATION_WAIT=0.5 # How long to wait for namespace creation (seconds)

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

# Helper function to execute commands with or without su
run_cmd() {
    if [ "$USE_SU" != "0" ]; then
        su -c "$1"
    else
        sh -c "$1"
    fi
}

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
    # Pass NSENTER_MODE=1 to signal that we're joining an existing namespace
    run_cmd "NSENTER_MODE=1 nsenter -t $NS_PID -m -p -u -i chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\""
else
    # Create new namespace with init as PID 1 (first session)
    # -a: all supported namespaces, -f: fork
    run_cmd "
        unshare -a -f sh -c '
            # Mount proc filesystem (required for PID namespace, init becomes PID 1)
            mount -t proc proc \"$ALPINE_DIR/proc\" 2>/dev/null || true
            
            # Save PID to file for future sessions
            echo \$\$ > \"$NAMESPACE_PID_FILE\"
            
            # Set up all mounts inline
            mount --bind /sdcard \"$ALPINE_DIR/sdcard\" 2>/dev/null || true
            mount --bind /storage \"$ALPINE_DIR/storage\" 2>/dev/null || true
            mount --bind /dev \"$ALPINE_DIR/dev\" 2>/dev/null || true
            mount --bind /sys \"$ALPINE_DIR/sys\" 2>/dev/null || true
            mount --bind \"$PREFIX\" \"$ALPINE_DIR$PREFIX\" 2>/dev/null || true
            mount --bind \"$PREFIX/local/stat\" \"$ALPINE_DIR/proc/stat\" 2>/dev/null || true
            mount --bind \"$PREFIX/local/vmstat\" \"$ALPINE_DIR/proc/vmstat\" 2>/dev/null || true
            mount --bind \"$PREFIX/local/alpine/tmp\" \"$ALPINE_DIR/dev/shm\" 2>/dev/null || true
            
            for system_mnt in /apex /odm /product /system /system_ext /vendor; do
                if [ -e \"\$system_mnt\" ]; then
                    mount --bind \"\$system_mnt\" \"$ALPINE_DIR\$system_mnt\" 2>/dev/null || true
                fi
            done
            
            if [ -e /linkerconfig/ld.config.txt ]; then
                mount --bind /linkerconfig/ld.config.txt \"$ALPINE_DIR/linkerconfig/ld.config.txt\" 2>/dev/null || true
            fi
            
            if [ -e /linkerconfig/com.android.art/ld.config.txt ]; then
                mount --bind /linkerconfig/com.android.art/ld.config.txt \"$ALPINE_DIR/linkerconfig/com.android.art/ld.config.txt\" 2>/dev/null || true
            fi
            
            if [ -e /plat_property_contexts ]; then
                mount --bind /plat_property_contexts \"$ALPINE_DIR/plat_property_contexts\" 2>/dev/null || true
            fi
            
            if [ -e /property_contexts ]; then
                mount --bind /property_contexts \"$ALPINE_DIR/property_contexts\" 2>/dev/null || true
            fi
            
            mount --bind /dev/urandom \"$ALPINE_DIR/dev/random\" 2>/dev/null || true
            
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
            
            # Execute init as PID 1 in the namespace
            # With -p -f flags and proc mounted, init becomes PID 1
            exec chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\"
        '
    " &
    
    # Wait for namespace to be created
    sleep $NAMESPACE_CREATION_WAIT
    
    # First session: Enter the newly created namespace
    # Pass NSENTER_MODE=1 since we're entering via nsenter
    if [ -f "$NAMESPACE_PID_FILE" ]; then
        NS_PID=$(cat "$NAMESPACE_PID_FILE" 2>/dev/null)
        if [ -n "$NS_PID" ]; then
            if run_cmd "kill -0 $NS_PID 2>/dev/null"; then
                run_cmd "NSENTER_MODE=1 nsenter -t $NS_PID -m -p -u -i chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\""
            fi
        fi
    fi
fi
