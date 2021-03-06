package uk.ac.ebi.ena.sra.cram.impl;

import java.io.DataInputStream;
import java.io.IOException;

import uk.ac.ebi.ena.sra.cram.SequenceBaseProvider;
import uk.ac.ebi.ena.sra.cram.encoding.BitCodec;
import uk.ac.ebi.ena.sra.cram.format.CramFormatException;
import uk.ac.ebi.ena.sra.cram.format.CramHeader;
import uk.ac.ebi.ena.sra.cram.format.CramRecord;
import uk.ac.ebi.ena.sra.cram.format.CramRecordBlock;
import uk.ac.ebi.ena.sra.cram.format.compression.CramCompressionException;
import uk.ac.ebi.ena.sra.cram.io.DefaultBitInputStream;

public class SequentialCramReader {

	private CramRecordBlockReader blockReader;
	private CramRecordBlock block;

	private BitCodec<CramRecord> recordCodec;
	private DefaultBitInputStream bis;

	private long awaitingRecords = -1L;

	private DataInputStream dis;
	private RecordCodecFactory recordCodecFactory = new RecordCodecFactory();
	private SequenceBaseProvider referenceBaseProvider;

	private RestoreBases restoreBases;
	private final CramHeader header;

	public SequentialCramReader(DataInputStream dis,
			SequenceBaseProvider referenceBaseProvider, CramHeader header) {
		this.dis = dis;
		this.referenceBaseProvider = referenceBaseProvider;
		this.header = header;
		blockReader = new CramRecordBlockReader(dis);
	}

	public void reset() {
		block = null;
	}

	public void skipBits(byte bits) throws IOException {
		bis.reset();
		bis.readBits(bits);
	}

	public byte getHangingBits() {
		byte bitOffset = (byte) bis.getNofBufferedBits();
		if (bitOffset > 0)
			bitOffset = (byte) (8 - bitOffset);
		return bitOffset;
	}

	public CramRecordBlock readBlock() throws IOException,
			CramCompressionException, CramFormatException {
		// if (awaitingRecords > 0L)
		// throw new RuntimeException("Pending records found. ");
		block = blockReader.read();
		if (block == null) {
			awaitingRecords = -1;
			referenceBaseProvider = null;
			recordCodec = null;
			restoreBases = null;
			return null;
		}
		awaitingRecords = block.getRecordCount();
		bis = new DefaultBitInputStream(dis);

		if (referenceBaseProvider != null) {
			recordCodec = recordCodecFactory.createRecordCodec(header, block,
					referenceBaseProvider);
			restoreBases = new RestoreBases(referenceBaseProvider,
					block.getSequenceName());
		}
		return block;
	}

	public CramRecord readRecord() throws IOException {
		if (block == null)
			return null;
		if (awaitingRecords < 1)
			return null;
		CramRecord record = recordCodec.read(bis);
		record.setSequenceName(block.getSequenceName());
		if (record.isReadMapped())
			restoreBases.restoreReadBases(record);
		awaitingRecords--;
		return record;
	}

	public SequenceBaseProvider getReferenceBaseProvider() {
		return referenceBaseProvider;
	}

	public void setReferenceBaseProvider(
			SequenceBaseProvider referenceBaseProvider)
			throws CramCompressionException {
		this.referenceBaseProvider = referenceBaseProvider;
		if (block != null) {
			recordCodec = recordCodecFactory.createRecordCodec(header, block,
					referenceBaseProvider);
			restoreBases = new RestoreBases(referenceBaseProvider,
					block.getSequenceName());
		}
	}
}
