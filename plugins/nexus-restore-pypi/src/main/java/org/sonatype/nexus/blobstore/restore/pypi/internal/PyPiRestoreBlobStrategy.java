/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.blobstore.restore.pypi.internal;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.blobstore.restore.BaseRestoreBlobStrategy;
import org.sonatype.nexus.blobstore.restore.RestoreBlobData;
import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.common.log.DryRunPrefix;
import org.sonatype.nexus.common.node.NodeAccess;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.pypi.PyPiFacet;
import org.sonatype.nexus.repository.pypi.repair.PyPiRepairIndexComponent;
import org.sonatype.nexus.repository.storage.AssetBlob;
import org.sonatype.nexus.repository.transaction.TransactionalStoreMetadata;
import org.sonatype.nexus.transaction.Transactional;

import com.google.common.collect.ImmutableList;

import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA256;

/**
 * @since 3.14
 */
@Named("pypi")
@Singleton
public class PyPiRestoreBlobStrategy
    extends BaseRestoreBlobStrategy<PyPiRestoreBlobData>
{
  private final PyPiRepairIndexComponent pyPiRepairIndexComponent;

  @Inject
  public PyPiRestoreBlobStrategy(final NodeAccess nodeAccess,
                                 final RepositoryManager repositoryManager,
                                 final BlobStoreManager blobStoreManager,
                                 final DryRunPrefix dryRunPrefix,
                                 final PyPiRepairIndexComponent pyPiRepairIndexComponent)
  {
    super(nodeAccess, repositoryManager, blobStoreManager, dryRunPrefix);
    this.pyPiRepairIndexComponent = pyPiRepairIndexComponent;
  }

  @Override
  protected PyPiRestoreBlobData createRestoreData(final RestoreBlobData blobData) {
    return new PyPiRestoreBlobData(blobData);
  }

  @Override
  protected boolean canAttemptRestore(final PyPiRestoreBlobData data) {
    Repository repository = data.getBlobData().getRepository();

    if (!repository.optionalFacet(PyPiFacet.class).isPresent()) {
      log.warn("Skipping as PyPI Facet not found on repository: {}", repository.getName());
      return false;
    }

    return true;
  }

  @Override
  protected String getAssetPath(final PyPiRestoreBlobData data) {
    return data.getBlobData().getBlobName();
  }

  @Transactional
  @Override
  protected boolean assetExists(final PyPiRestoreBlobData data) throws IOException {
    return data.getBlobData().getRepository().facet(PyPiFacet.class).assetExists(getAssetPath(data));
  }

  @TransactionalStoreMetadata
  @Override
  protected void createAssetFromBlob(final AssetBlob assetBlob, final PyPiRestoreBlobData data)
      throws IOException
  {
    data.getBlobData().getRepository().facet(PyPiFacet.class).put(getAssetPath(data), assetBlob);
  }

  @Override
  public void after(final boolean updateAssets, final Repository repository) {
    if (updateAssets) {
      pyPiRepairIndexComponent.repairRepository(repository);
    }
    else {
      log.info("Updating assets disabled so not running repair of PyPi package metadata");
    }
  }

  @Override
  protected List<HashAlgorithm> getHashAlgorithms() {
    return ImmutableList.of(SHA1, SHA256, MD5);
  }
}
