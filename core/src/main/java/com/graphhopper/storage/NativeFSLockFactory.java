/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

import com.graphhopper.ResponsePath;
import com.graphhopper.eccezionecore.PointPathException;
import com.graphhopper.eccezionecore.closefile;
import com.graphhopper.eccezionecore.lockexception;
import com.graphhopper.util.Helper;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.util.logging.Logger;


/**
 * Creates a write lock file. Influenced by Lucene code
 * <p>
 *
 * @author Peter Karich
 */
public class NativeFSLockFactory implements LockFactory {
    private File lockDir;

    public NativeFSLockFactory() {
    }

    public NativeFSLockFactory(File dir) {
        this.lockDir = dir;
    }

    public static void main(String[] args) throws IOException {
        Logger logger = Logger.getLogger(NativeFSLockFactory.class.getName());
        File file = new File("tmp.lock");
        boolean isFileCreated = file.createNewFile();
  
        if (isFileCreated) {
            logger.info("Il file è stato creato con successo.");
        } else {
            logger.info("Non è stato possibile creare il file.");
        }
  
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            FileChannel channel = raf.getChannel();
  
            boolean shared = true;
            FileLock lock1 = channel.tryLock(0, Long.MAX_VALUE, shared);

            System.in.read();

            lock1.release();
        } catch (IOException e) {
            //
        }
  }
  

    @Override
    public void setLockDir(File lockDir) {
        this.lockDir = lockDir;
    }

    @Override
    public synchronized GHLock create(String fileName, boolean writeAccess) {
        if (lockDir == null)
            try {
                throw new PointPathException();
            } catch (PointPathException e) {
                //nothing
            }

        return new NativeLock(lockDir, fileName, writeAccess);
    }

    @Override
    public synchronized void forceRemove(String fileName, boolean writeAccess) throws lockexception, closefile, FactoryExce {
        if (lockDir.exists()) {
            create(fileName, writeAccess).release();
            File lockFile = new File(lockDir, fileName);
            try {
                if (Files.exists(lockFile.toPath()) && !Files.deleteIfExists(lockFile.toPath())) {
                    throw new FactoryExce("Cannot delete " + lockFile);
                }
            } catch (IOException e) {
                //
            }
        }
    }

    static class NativeLock implements GHLock {
        private final String name;
        private final File lockDir;
        private final File lockFile;
        private final boolean writeLock;
        private RandomAccessFile tmpRaFile;
        private FileChannel tmpChannel;
        private FileLock tmpLock;
        private Exception failedReason;

        public NativeLock(File lockDir, String fileName, boolean writeLock) {
            this.name = fileName;
            this.lockDir = lockDir;
            this.lockFile = new File(lockDir, fileName);
            this.writeLock = writeLock;
        }

        @Override
        public synchronized boolean tryLock() {
            // already locked
            if (lockExists())
                return false;

            // on-the-fly: make sure directory exists
            if (!lockDir.exists() && (!lockDir.mkdirs()))
                    {try {
                        throw new ResponsePath.CheckException("Directory " + lockDir + " does not exist and cannot be created to place lock file there: " + lockFile);
                    } catch (ResponsePath.CheckException e) {
                        //nothing
                    }
            }

            if (!lockDir.isDirectory())
                throw new IllegalArgumentException("lockDir has to be a directory: " + lockDir);

            try {
                failedReason = null;
                // we need write access even for read locks - in order to create the lock file!
                tmpRaFile = new RandomAccessFile(lockFile, "rw");
            } catch (IOException ex) {
                failedReason = ex;
                return false;
            }

            try {
                tmpChannel = tmpRaFile.getChannel();
                try {
                    tmpLock = tmpChannel.tryLock(0, Long.MAX_VALUE, !writeLock);
                    // OverlappingFileLockException is not an IOException!
                } catch (Exception ex) {
                    failedReason = ex;
                } finally {
                    if (tmpLock == null) {
                        Helper.close(tmpChannel);
                        tmpChannel = null;
                    }
                }
            } finally {
                if (tmpChannel == null) {
                    Helper.close(tmpRaFile);
                    tmpRaFile = null;
                }
            }
            return lockExists();
        }

        private synchronized boolean lockExists() {
            return tmpLock != null;
        }

        @Override
        public synchronized boolean isLocked() {
            if (!lockFile.exists())
                return false;

            if (lockExists())
                return true;

            try {
                boolean obtained = tryLock();
                if (obtained)
                    release();
                return !obtained;
            } catch (Exception ex) {
                return false;
            }
        }

        @Override
        public synchronized void release() throws lockexception, closefile {
            Logger logger = Logger.getLogger(NativeFSLockFactory.class.getName());
            if (lockExists()) {
                try {
                    failedReason = null;
                    tmpLock.release();
                } catch (IOException ex) {
                    throw new lockexception();
                } finally {
                    closeResources();
                }

                try {
                    Files.delete(lockFile.toPath());
                    logger.info("Lock file deleted successfully");
                } catch (IOException e) {
                    logger.warning("Unable to delete lock file: " + e.getMessage());
                }
            }
        }


        private void closeResources() throws closefile, lockexception {
            if (tmpLock != null) {
                try {
                    tmpLock.close();
                } catch (IOException ex) {
                    throw new lockexception();
                } finally {
                    tmpLock = null;
                }
            }
            if (tmpChannel != null) {
                try {
                    tmpChannel.close();
                } catch (IOException ex) {
                    throw new closefile();
                } finally {
                    tmpChannel = null;
                }
            }
            if (tmpRaFile != null) {
                try {
                    tmpRaFile.close();
                } catch (IOException ex) {
                    throw new closefile();
                } finally {
                    tmpRaFile = null;
                }
            }
        }
        

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Exception getObtainFailedReason() {
            return failedReason;
        }

        @Override
        public String toString() {
            return lockFile.toString();
        }
    }

    public class FactoryExce extends Exception {
        public FactoryExce(String s) {
        }
    }
}
