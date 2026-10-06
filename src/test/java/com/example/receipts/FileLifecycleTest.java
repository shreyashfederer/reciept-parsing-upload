package com.example.receipts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.receipts.receipt.FileStore;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class FileLifecycleTest extends ApiTestBase {

    @MockitoSpyBean
    FileStore fileStore;

    /** Same bytes re-uploaded after DELETE committed but before its file removal ran. */
    @Test
    void reuploadRacingDeleteKeepsItsOwnFile() throws Exception {
        long original = upload("receipt-clean.txt");
        AtomicLong reuploaded = new AtomicLong();
        doAnswer(inv -> {
            reuploaded.set(upload("receipt-clean.txt"));
            return inv.callRealMethod();
        }).when(fileStore).delete(any(Path.class));

        mvc.perform(delete("/receipts/{id}", original)).andExpect(status().isNoContent());

        process(reuploaded.get());
    }
}
