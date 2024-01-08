package com.cft.services;

import com.cft.entities.CFTEvent;
import com.cft.entities.CFTEventSnapshot;
import lombok.NonNull;

import java.io.IOException;
import java.net.URI;

public interface ISnapshotService {
    CFTEventSnapshot takeEventSnapshot(@NonNull CFTEvent event);
    CFTEventSnapshot uploadSnapshotToGoogleSheets(@NonNull CFTEventSnapshot snapshot) throws IOException;
}
