#!/system/bin/sh

ALPINE_DIR=$PREFIX/local/alpine
NAMESPACE_PID_FILE=$PREFIX/local/.alpine-ns-pid

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

# Function to set up mounts (must be done inside namespace)
setup_mounts() {
    # Mount proc for the PID namespace
    mount -t proc proc "$ALPINE_DIR/proc" 2>/dev/null || true
    
    # Bind mount necessary directories
    mount --bind /sdcard "$ALPINE_DIR/sdcard" 2>/dev/null || true
    mount --bind /storage "$ALPINE_DIR/storage" 2>/dev/null || true
    mount --bind /dev "$ALPINE_DIR/dev" 2>/dev/null || true
    mount --bind /sys "$ALPINE_DIR/sys" 2>/dev/null || true
    mount --bind "$PREFIX" "$ALPINE_DIR$PREFIX" 2>/dev/null || true
    mount --bind "$PREFIX/local/stat" "$ALPINE_DIR/proc/stat" 2>/dev/null || true
    mount --bind "$PREFIX/local/vmstat" "$ALPINE_DIR/proc/vmstat" 2>/dev/null || true
    
    # Bind mount /dev/shm
    mount --bind "$PREFIX/local/alpine/tmp" "$ALPINE_DIR/dev/shm" 2>/dev/null || true
    
    # Bind mount system directories
    for system_mnt in /apex /odm /product /system /system_ext /vendor; do
        if [ -e "$system_mnt" ]; then
            mount --bind "$system_mnt" "$ALPINE_DIR$system_mnt" 2>/dev/null || true
        fi
    done
    
    # Bind mount linkerconfig if present
    if [ -e /linkerconfig/ld.config.txt ]; then
        mount --bind /linkerconfig/ld.config.txt "$ALPINE_DIR/linkerconfig/ld.config.txt" 2>/dev/null || true
    fi
    
    if [ -e /linkerconfig/com.android.art/ld.config.txt ]; then
        mount --bind /linkerconfig/com.android.art/ld.config.txt "$ALPINE_DIR/linkerconfig/com.android.art/ld.config.txt" 2>/dev/null || true
    fi
    
    # Bind mount property contexts
    if [ -e /plat_property_contexts ]; then
        mount --bind /plat_property_contexts "$ALPINE_DIR/plat_property_contexts" 2>/dev/null || true
    fi
    
    if [ -e /property_contexts ]; then
        mount --bind /property_contexts "$ALPINE_DIR/property_contexts" 2>/dev/null || true
    fi
    
    # Bind mount /dev/random
    mount --bind /dev/urandom "$ALPINE_DIR/dev/random" 2>/dev/null || true
    
    # Set up /dev/fd, stdin, stdout, stderr
    if [ -e /proc/self/fd ]; then
        mount --bind /proc/self/fd "$ALPINE_DIR/dev/fd" 2>/dev/null || true
    fi
    
    if [ -e /proc/self/fd/0 ]; then
        mount --bind /proc/self/fd/0 "$ALPINE_DIR/dev/stdin" 2>/dev/null || true
    fi
    
    if [ -e /proc/self/fd/1 ]; then
        mount --bind /proc/self/fd/1 "$ALPINE_DIR/dev/stdout" 2>/dev/null || true
    fi
    
    if [ -e /proc/self/fd/2 ]; then
        mount --bind /proc/self/fd/2 "$ALPINE_DIR/dev/stderr" 2>/dev/null || true
    fi
}

# Check if namespace already exists and is valid
NS_EXISTS=0
if [ -f "$NAMESPACE_PID_FILE" ]; then
    NS_PID=$(cat "$NAMESPACE_PID_FILE" 2>/dev/null)
    if [ -n "$NS_PID" ]; then
        # Check if the process still exists
        if su -c "kill -0 $NS_PID 2>/dev/null"; then
            NS_EXISTS=1
        else
            # Process is dead, remove stale PID file
            rm -f "$NAMESPACE_PID_FILE"
        fi
    fi
fi

if [ "$NS_EXISTS" = "1" ]; then
    # Namespace exists, enter it using nsenter
    exec su -c "nsenter -t $NS_PID -m -p -u -i chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\""
else
    # Create new namespace and keep it alive
    # Start a background process to maintain the namespace
    su -c "
        unshare -m -p -u -i -f sh -c '
            # Save PID to file for future sessions
            echo \$\$ > \"$NAMESPACE_PID_FILE\"
            
            # Set up all mounts
            $(declare -f setup_mounts)
            setup_mounts
            
            # Start a background sleep to keep namespace alive
            ( while true; do sleep 3600; done ) &
            KEEPER_PID=\$!
            
            # Run the actual session
            chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\"
            
            # Clean up: kill keeper and remove PID file
            kill \$KEEPER_PID 2>/dev/null || true
            rm -f \"$NAMESPACE_PID_FILE\"
        '
    " &
    
    # Wait a moment for namespace to be created
    sleep 0.5
    
    # Try to enter the newly created namespace
    if [ -f "$NAMESPACE_PID_FILE" ]; then
        NS_PID=$(cat "$NAMESPACE_PID_FILE" 2>/dev/null)
        if [ -n "$NS_PID" ] && su -c "kill -0 $NS_PID 2>/dev/null"; then
            exec su -c "nsenter -t $NS_PID -m -p -u -i chroot \"$ALPINE_DIR\" /bin/sh \"$PREFIX/local/bin/init\" \"\$@\""
        fi
    fi
    
    # Fallback: wait for background process
    wait
fi
