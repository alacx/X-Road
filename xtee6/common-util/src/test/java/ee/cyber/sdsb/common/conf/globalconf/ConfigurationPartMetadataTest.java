package ee.cyber.sdsb.common.conf.globalconf;

import java.io.ByteArrayInputStream;

import org.joda.time.DateTime;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigurationPartMetadataTest {

    @Test
    public void writeReadMetadata() throws Exception {
        ConfigurationPartMetadata write = new ConfigurationPartMetadata();
        write.setContentIdentifier("SHARED-PARAMETERS");
        write.setInstanceIdentifier("FOO");
        write.setExpirationDate(new DateTime());

        ConfigurationPartMetadata read = ConfigurationPartMetadata.read(
                new ByteArrayInputStream(write.toByteArray()));

        assertEquals(write.getContentIdentifier(), read.getContentIdentifier());
        assertEquals(write.getInstanceIdentifier(),
                read.getInstanceIdentifier());
        assertEquals(write.getExpirationDate(), read.getExpirationDate());
    }

}