package com.republicate.skorm.config;

import com.republicate.skorm.jdbc.Connection;
import com.republicate.skorm.jdbc.Vendor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        java.sql.Connection connection = DriverManager.getConnection(url);
        Connection wrapper = new Connection(connection);
        Vendor vendor = wrapper.getVendor();
        assertEquals("h2", vendor.getTag());
    }

    @Mock
    DatabaseMetaData mockedDbMetaData;

    @Mock
    java.sql.Connection mockedConnection;

    @Test
    void testVendorByMetaData() throws Exception
    {
        when(mockedConnection.getMetaData()).thenReturn(mockedDbMetaData);
        when(mockedDbMetaData.getURL()).thenReturn("jdbc:dummy:");
        when(mockedDbMetaData.storesLowerCaseIdentifiers()).thenReturn(true);
        when(mockedDbMetaData.getIdentifierQuoteString()).thenReturn("`");

        Connection wrapper = new Connection(mockedConnection);
        Vendor vendor = wrapper.getVendor();
        assertEquals("dummy", vendor.getTag());
        assertEquals(Vendor.CaseSensitivity.LOWERCASE, vendor.getTablesCaseSensitivity());
        assertEquals('`', vendor.getIdentifierQuoteChar());
    }
}
