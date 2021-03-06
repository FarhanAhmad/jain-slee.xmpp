/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jivesoftware.smackx.filetransfer;

import org.jivesoftware.smack.XMPPException;

import java.io.*;

/**
 * An incoming file transfer is created when the
 * {@link FileTransferManager#createIncomingFileTransfer(FileTransferRequest)}
 * method is invoked. It is a file being sent to the local user from another
 * user on the jabber network. There are two stages of the file transfer to be
 * concerned with and they can be handled in different ways depending upon the
 * method that is invoked on this class.
 * <p/>
 * The first way that a file is recieved is by calling the
 * {@link #recieveFile()} method. This method, negotiates the appropriate stream
 * method and then returns the <b><i>InputStream</b></i> to read the file
 * data from.
 * <p/>
 * The second way that a file can be recieved through this class is by invoking
 * the {@link #recieveFile(File)} method. This method returns immediatly and
 * takes as its parameter a file on the local file system where the file
 * recieved from the transfer will be put.
 *
 * @author Alexander Wenckus
 */
public class IncomingFileTransfer extends FileTransfer {

    private FileTransferRequest recieveRequest;

    private Thread transferThread;

    private InputStream inputStream;

    protected IncomingFileTransfer(FileTransferRequest request,
            FileTransferNegotiator transferNegotiator) {
        super(request.getRequestor(), request.getStreamID(), transferNegotiator);
        this.recieveRequest = request;
    }

    /**
     * Negotiates the stream method to transfer the file over and then returns
     * the negotiated stream.
     *
     * @return The negotiated InputStream from which to read the data.
     * @throws XMPPException If there is an error in the negotiation process an exception
     *                       is thrown.
     */
    public InputStream recieveFile() throws XMPPException {
        if (inputStream != null) {
            throw new IllegalStateException("Transfer already negotiated!");
        }

        try {
            inputStream = negotiateStream();
        }
        catch (XMPPException e) {
            setException(e);
            throw e;
        }

        return inputStream;
    }

    /**
     * This method negotitates the stream and then transfer's the file over the
     * negotiated stream. The transfered file will be saved at the provided
     * location.
     * <p/>
     * This method will return immedialtly, file transfer progress can be
     * monitored through several methods:
     * <p/>
     * <UL>
     * <LI>{@link FileTransfer#getStatus()}
     * <LI>{@link FileTransfer#getProgress()}
     * <LI>{@link FileTransfer#isDone()}
     * </UL>
     *
     * @param file The location to save the file.
     * @throws XMPPException
     * @throws IllegalArgumentException This exception is thrown when the the provided file is
     *                                  either null, or cannot be written to.
     */
    public void recieveFile(final File file) throws XMPPException {
        if (file != null) {
            if (!file.exists()) {
                try {
                    file.createNewFile();
                }
                catch (IOException e) {
                    throw new XMPPException(
                            "Could not create file to write too", e);
                }
            }
            if (!file.canWrite()) {
                throw new IllegalArgumentException("Cannot write to provided file");
            }
        }
        else {
            throw new IllegalArgumentException("File cannot be null");
        }

        transferThread = new Thread(new Runnable() {
            public void run() {
                try {
                    inputStream = negotiateStream();
                }
                catch (XMPPException e) {
                    handleXMPPException(e);
                    return;
                }

                OutputStream outputStream = null;
                try {
                    outputStream = new FileOutputStream(file);
                    setStatus(Status.IN_PROGRESS);
                    writeToStream(inputStream, outputStream);
                }
                catch (XMPPException e) {
                    setStatus(FileTransfer.Status.ERROR);
                    setError(Error.STREAM);
                    setException(e);
                }
                catch (FileNotFoundException e) {
                    setStatus(FileTransfer.Status.ERROR);
                    setError(Error.BAD_FILE);
                    setException(e);
                }

                if (getStatus().equals(Status.IN_PROGRESS))
                    setStatus(Status.COMPLETE);
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                }
                catch (IOException e) {
                }
            }
        }, "File Transfer " + streamID);
        transferThread.start();

    }

    private void handleXMPPException(XMPPException e) {
        setStatus(FileTransfer.Status.ERROR);
        setException(e);
    }

    private InputStream negotiateStream() throws XMPPException {
        setStatus(Status.NEGOTIATING_TRANSFER);
        StreamNegotiator streamNegotiator = negotiator
                .selectStreamNegotiator(recieveRequest);
        setStatus(Status.NEGOTIATING_STREAM);
        InputStream inputStream = streamNegotiator
                .createIncomingStream(recieveRequest.getStreamInitiation());
        setStatus(Status.NEGOTIATED);
        return inputStream;
    }

    public void cancel() {
        setStatus(Status.CANCLED);
    }

}
