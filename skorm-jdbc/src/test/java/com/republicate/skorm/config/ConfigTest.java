package com.republicate.skorm.config;

import com.republicate.skorm.jdbc.ConnectionWrapper;
import com.republicate.skorm.jdbc.Vendor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConfigTest
{
    @Test
    void testVendorByProperties() throws Exception
    {
        String url = "jdbc:h2:mem:test";
        Connection connection = DriverManager.getConnection(url);
        ConnectionWrapper wrapper = new ConnectionWrapper(connection);
        Vendor vendor = wrapper.getVendor();
        assertEquals("h2", vendor.getTag());
    }

    @Mock
    DatabaseMetaData mockedDbMetaData;

    @Mock
    Connection mockedConnection;

    @Test
    void testVendorByMetaData() throws Exception
    {
        when(mockedConnection.getMetaData()).thenReturn(mockedDbMetaData);
        when(mockedDbMetaData.getURL()).thenReturn("jdbc:dummy:");
        when(mockedDbMetaData.storesLowerCaseIdentifiers()).thenReturn(true);
        when(mockedDbMetaData.getIdentifierQuoteString()).thenReturn("`");

        ConnectionWrapper wrapper = new ConnectionWrapper(mockedConnection);
        Vendor vendor = wrapper.getVendor();
        assertEquals("dummy", vendor.getTag());
        assertEquals(Vendor.CaseSensitivity.LOWERCASE, vendor.getTablesCaseSensitivity());
        assertEquals('`', vendor.getIdentifierQuoteChar());
    }
}
