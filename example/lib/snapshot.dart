// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:mapbox_gl/mapbox_gl.dart';

import 'main.dart';
import 'page.dart';

class SnapshotPage extends ExamplePage {
  SnapshotPage() : super(const Icon(Icons.map), 'Take a snapshot of the map');

  @override
  Widget build(BuildContext context) {
    return const _SnapshotBody();
  }
}

class _SnapshotBody extends StatefulWidget {
  const _SnapshotBody();

  @override
  State createState() => _SnapshotBodyState();
}

class _SnapshotBodyState extends State<_SnapshotBody> {
  late MapboxMapController _mapController;

  Uint8List? _imageBytes;

  void _onMapCreated(MapboxMapController controller) {
    _mapController = controller;
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: <Widget>[
          SizedBox(
            width: 300.0,
            height: 200.0,
            child: MapboxMap(
              accessToken: MapsDemo.ACCESS_TOKEN,
              onMapCreated: _onMapCreated,
              onCameraIdle: () => print("onCameraIdle"),
              initialCameraPosition:
                  const CameraPosition(target: LatLng(0.0, 0.0)),
            ),
          ),
          TextButton(
            child: Text('Take a snapshot'),
            onPressed: () async {
              final imageBytes = await _mapController.takeSnapshot();
              setState(() {
                _imageBytes = imageBytes;
              });
            },
          ),
          Container(
            decoration: BoxDecoration(color: Colors.blueGrey[50]),
            width: 300,
            height: 200,
            child: _imageBytes != null ? Image.memory(_imageBytes!) : null,
          ),
        ],
      ),
    );
  }
}
