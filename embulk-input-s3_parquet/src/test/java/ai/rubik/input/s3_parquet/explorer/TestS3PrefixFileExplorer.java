package ai.rubik.input.s3_parquet.explorer;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.StorageClass;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.embulk.EmbulkTestRuntime;
import org.embulk.config.ConfigException;
import ai.rubik.input.s3_parquet.FileList;
import org.embulk.spi.util.RetryExecutor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TestS3PrefixFileExplorer
{
    private static final String PATH_PREFIX = "path_prefix";
    private static final String BUCKET_NAME = "bucket_name";
    private static final String OBJECT_KEY = "key";

    @SuppressFBWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    @Rule
    public EmbulkTestRuntime embulkTestRuntime = new EmbulkTestRuntime();

    @Mock
    private AmazonS3 s3Client;

    @Mock
    private FileList.Builder builder;

    @Mock
    private S3ObjectSummary s3ObjectSummary;

    private S3PrefixFileExplorer s3PrefixFileExplorer;

    @Before
    public void setUp()
    {
        s3PrefixFileExplorer = spyS3PrefixFileExplorer(BUCKET_NAME, s3Client, null, PATH_PREFIX, false);
        doReturn(Collections.singletonList(s3ObjectSummary)).when(s3PrefixFileExplorer).fetch();
    }

    @Test(expected = ConfigException.class)
    public void addToBuilder_should_throw_exception_if_notskipped_glacier_storage()
    {
        when(s3ObjectSummary.getStorageClass()).thenReturn(StorageClass.Glacier.toString());
        s3PrefixFileExplorer.addToBuilder(builder);
    }

    @Test
    public void addToBuilder_should_skip_glacier_storage_if_allowed()
    {
        when(s3ObjectSummary.getStorageClass()).thenReturn(StorageClass.Glacier.toString());
        // override spied object for changing `skipGlacierObjects`
        s3PrefixFileExplorer = spyS3PrefixFileExplorer(BUCKET_NAME, s3Client, null, PATH_PREFIX, true);
        doReturn(false).when(s3PrefixFileExplorer).hasNext();
        doReturn(Collections.singletonList(s3ObjectSummary)).when(s3PrefixFileExplorer).fetch();
        s3PrefixFileExplorer.addToBuilder(builder);

        verify(s3PrefixFileExplorer).hasNext();
        verify(s3ObjectSummary, never()).getSize();
    }

    @Test
    public void addToBuilder_should_loop_till_nothing_left()
    {
        // There are 3 loops totally but only 2 keys have been imported because the first key is in Glacier storage class and is skipped
        when(builder.needsMore()).thenReturn(true);
        // override spied object for changing `skipGlacierObjects`
        s3PrefixFileExplorer = spyS3PrefixFileExplorer(BUCKET_NAME, s3Client, null, PATH_PREFIX, true);
        when(s3ObjectSummary.getStorageClass())
                .thenReturn(StorageClass.Glacier.toString())
                .thenReturn(StorageClass.Standard.toString());
        when(s3ObjectSummary.getSize()).thenReturn(1L);
        when(s3ObjectSummary.getKey()).thenReturn(PATH_PREFIX + OBJECT_KEY);
        doReturn(Collections.singletonList(s3ObjectSummary)).when(s3PrefixFileExplorer).fetch();
        doReturn(true).doReturn(true).doReturn(false).when(s3PrefixFileExplorer).hasNext();

        s3PrefixFileExplorer.addToBuilder(builder);
        verify(builder, times(2)).add(PATH_PREFIX + OBJECT_KEY, 1);
    }

    @Test
    public void addToBuilder_should_stop_import_if_too_many_files()
    {
        when(builder.needsMore()).thenReturn(false);
        when(s3ObjectSummary.getStorageClass()).thenReturn(StorageClass.Standard.toString());
        when(s3ObjectSummary.getKey()).thenReturn(PATH_PREFIX + OBJECT_KEY);
        when(s3ObjectSummary.getSize()).thenReturn(1L);
        doReturn(true).when(s3PrefixFileExplorer).hasNext();
        s3PrefixFileExplorer.addToBuilder(builder);

        verify(builder).add(PATH_PREFIX + OBJECT_KEY, 1);
        verify(s3PrefixFileExplorer, never()).hasNext();
    }

    private S3PrefixFileExplorer spyS3PrefixFileExplorer(final String bucketName, final AmazonS3 s3Client, final RetryExecutor retryExecutor, final String pathPrefix, final boolean skipGlacierObjects)
    {
        return spy(new S3PrefixFileExplorer(bucketName, s3Client, retryExecutor, pathPrefix, skipGlacierObjects)
        {
            @Override
            protected List<S3ObjectSummary> fetch()
            {
                return null;
            }

            @Override
            protected boolean hasNext()
            {
                return false;
            }
        });
    }
}
