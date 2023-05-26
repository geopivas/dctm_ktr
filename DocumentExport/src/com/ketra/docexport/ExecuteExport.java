package com.ketra.docexport;

import java.util.Scanner;
import com.documentum.com.DfClientX;
import com.documentum.com.IDfClientX;
import com.documentum.fc.client.IDfClient;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSessionManager;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfLogger;
import com.documentum.fc.common.DfLoginInfo;

public class ExecuteExport
{
	IDfSession session;
	IDfSessionManager sMgr = null;
	
	public void executa(){
		Export ex = new DocumentExport(session);
		ex.doExport();
	}

	public ExecuteExport()
	{
		try
		{
			IDfClientX clientx = new DfClientX();
			IDfClient client = clientx.getLocalClient();
			sMgr = client.newSessionManager();
			DfLoginInfo loginInfo = new DfLoginInfo();
			System.out.println("Username:");
			loginInfo.setUser(new Scanner(System.in).next());
			System.out.println("Password:");
			loginInfo.setPassword(new Scanner(System.in).next());
			System.out.println("Repository:");
			String repository = new Scanner(System.in).next();
			sMgr = client.newSessionManager();
			sMgr.setIdentity(repository, loginInfo);
			session = sMgr.getSession(repository);

			System.out.println("Sessão criada para " + session.getUser(null).getUserName() + ". Repositório: " + session.getDocbaseName());
		}
		catch (DfException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args)
	{
		ExecuteExport su = new ExecuteExport();
		try{
			su.executa();
		}finally{
			su.encerra();
		}

	}

	private void encerra() {
		if (session != null){
			if (sMgr != null){
				sMgr.release(session);
			}else if (session.isConnected())
				try {
					session.disconnect();
				} catch (DfException e) {
					DfLogger.error(this, "Erro ao desconectar sessão: " + e.getMessage(), null, e);
				}
		}
	}
}