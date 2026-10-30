package local.mediacentre.client;
import local.mediacentre.client.data.PairIdentity;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public final class PairIdentityTest {
    private JSONObject value()throws Exception{return new JSONObject().put("data",new JSONObject().put("token",new String(new char[64]).replace('\0','a')).put("server_id","10000000-0000-4000-8000-000000000001").put("client_id","10000000-0000-4000-8000-000000000002").put("scopes",new JSONArray().put("preferences:read").put("favorites:write")));}
    @Test public void pairingIdentityRejectsMalformedSecretsAndHomeScope()throws Exception{
        new PairIdentity(value());JSONObject malformed=value();malformed.getJSONObject("data").put("token","short");
        try{new PairIdentity(malformed);fail("Short token accepted");}catch(JSONException expected){}
        malformed=value();malformed.getJSONObject("data").getJSONArray("scopes").put("home:control");
        try{new PairIdentity(malformed);fail("Home scope accepted");}catch(JSONException expected){}
    }
    @Test public void serverChangeRequiresPairingAndCodeHasNoAmbiguousCharacters()throws Exception{
        assertEquals("2345ABCD",PairIdentity.code(" 2345abcd "));
        try{PairIdentity.code("00000000");fail("Ambiguous code accepted");}catch(java.io.IOException expected){}
        PairIdentity.verifyServer("old","old");
        try{PairIdentity.verifyServer("old","new");fail("New server accepted");}catch(PairIdentity.ServerChanged expected){}
    }
}
