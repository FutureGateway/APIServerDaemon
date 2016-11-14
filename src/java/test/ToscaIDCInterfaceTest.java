/**************************************************************************
Copyright (c) 2011:
Istituto Nazionale di Fisica Nucleare (INFN), Italy
Consorzio COMETA (COMETA), Italy

See http://www.infn.it and and http://www.consorzio-cometa.it for details on
the copyright holders.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

@author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>(INFN)
****************************************************************************/
//package test;
import static org.junit.Assert.assertEquals;
import it.infn.ct.ToscaIDCInterface;
import org.junit.Test;

/**
 * ToscaIDCInterface tests
 * @author <a href="mailto:riccardo.bruno@ct.infn.it">Riccardo Bruno</a>
 */
public class ToscaIDCInterfaceTest {
    @Test
    public void ptvGetToken() {
        it.infn.ct.ToscaIDCInterface tIDCif = new it.infn.ct.ToscaIDCInterface();
        assertEquals(tIDCif.getPTVToken("AAABBBCCC").length() > 0, true);
    }
}
