package com.ketra.docexport;

import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfLogger;

public class Export {

	public IDfSession session = null;

	public void doExport() {

	}

	public Export(IDfSession session){
		this.session = session;
	}

	protected void fecharCollection(IDfCollection collection) {
		if (collection != null){
			try {collection.close();
			} catch (DfException e) {
				DfLogger.error(this, "Erro ao fechar collection: " + e.getMessage(), null, null);
			}
		}
	}
}

